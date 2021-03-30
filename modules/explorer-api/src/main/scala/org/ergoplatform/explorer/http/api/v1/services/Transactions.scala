package org.ergoplatform.explorer.http.api.v1.services

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.list._
import cats.{FlatMap, Monad}
import fs2.{Chunk, Pipe, Stream}
import mouse.anyf._
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.explorer.db.Trans
import org.ergoplatform.explorer.db.algebra.LiftConnectionIO
import org.ergoplatform.explorer.db.models.Transaction
import org.ergoplatform.explorer.db.repositories._
import org.ergoplatform.explorer.http.api.models.Sorting.SortOrder
import org.ergoplatform.explorer.http.api.models.{Items, Paging}
import org.ergoplatform.explorer.http.api.streaming.CompileStream
import org.ergoplatform.explorer.http.api.v1.models.TransactionInfo
import org.ergoplatform.explorer.settings.ServiceSettings
import org.ergoplatform.explorer.{Address, ErgoTreeTemplateHash, TxId}
import tofu.syntax.monadic._
import tofu.syntax.streams.compile._

trait Transactions[F[_]] {

  def get(id: TxId): F[Option[TransactionInfo]]

  def getByInputsScriptTemplate(
    template: ErgoTreeTemplateHash,
    paging: Paging,
    ordering: SortOrder
  ): F[Items[TransactionInfo]]

  def getByAddress(
    address: Address,
    paging: Paging
  ): F[Items[TransactionInfo]]
}

object Transactions {

  def apply[
    F[_]: Sync,
    D[_]: LiftConnectionIO: Monad: CompileStream
  ](serviceSettings: ServiceSettings)(trans: D Trans F)(implicit e: ErgoAddressEncoder): F[Transactions[F]] =
    (AssetRepo[F, D], InputRepo[F, D], OutputRepo[F, D], TransactionRepo[F, D], HeaderRepo[F, D]).mapN(
      new Live(serviceSettings, _, _, _, _, _)(trans)
    )

  final class Live[F[_]: FlatMap, D[_]: Monad: CompileStream](
    serviceSettings: ServiceSettings,
    assets: AssetRepo[D, Stream],
    inputs: InputRepo[D],
    outputs: OutputRepo[D, Stream],
    transactions: TransactionRepo[D, Stream],
    headers: HeaderRepo[D]
  )(trans: D Trans F)
    extends Transactions[F] {

    def get(id: TxId): F[Option[TransactionInfo]] = {
      val getTx =
        for {
          tx         <- OptionT(transactions.getMain(id))
          ins        <- OptionT.liftF(inputs.getFullByTxId(id))
          inIds      <- OptionT.fromOption(ins.map(_.input.boxId).toNel)
          inAssets   <- OptionT.liftF(assets.getAllByBoxIds(inIds))
          outs       <- OptionT.liftF(outputs.getAllByTxId(id))
          outIds     <- OptionT.fromOption(outs.map(_.output.boxId).toNel)
          outAssets  <- OptionT.liftF(assets.getAllByBoxIds(outIds))
          bestHeight <- OptionT.liftF(headers.getBestHeight)
          numConfirmations = tx.numConfirmations(bestHeight)
        } yield TransactionInfo.unFlatten(tx, numConfirmations, ins, outs, inAssets, outAssets)
      getTx.value.thrushK(trans.xa)
    }

    def getByInputsScriptTemplate(
      template: ErgoTreeTemplateHash,
      paging: Paging,
      ordering: SortOrder
    ): F[Items[TransactionInfo]] =
      transactions
        .countByInputsScriptTemplate(template)
        .flatMap { total =>
          transactions
            .getByInputsScriptTemplate(template, paging.offset, paging.limit, ordering.value)
            .chunkN(serviceSettings.chunkSize)
            .through(makeTransaction)
            .to[List]
            .map(Items(_, total))
        }
        .thrushK(trans.xa)

    def getByAddress(
      address: Address,
      paging: Paging
    ): F[Items[TransactionInfo]] =
      transactions
        .countRelatedToAddress(address)
        .flatMap { total =>
          transactions
            .streamRelatedToAddress(address, paging.offset, paging.limit)
            .chunkN(serviceSettings.chunkSize)
            .through(makeTransaction)
            .to[List]
            .map(Items(_, total))
        }
        .thrushK(trans.xa)

    private def makeTransaction: Pipe[D, Chunk[Transaction], TransactionInfo] =
      for {
        chunk      <- _
        txIds      <- Stream.emit(chunk.map(_.id).toNel).unNone
        ins        <- Stream.eval(inputs.getFullByTxIds(txIds))
        inIds      <- Stream.emit(ins.map(_.input.boxId).toNel).unNone
        inAssets   <- Stream.eval(assets.getAllByBoxIds(inIds))
        outs       <- Stream.eval(outputs.getAllByTxIds(txIds))
        outIds     <- Stream.emit(outs.map(_.output.boxId).toNel).unNone
        outAssets  <- Stream.eval(assets.getAllByBoxIds(outIds))
        bestHeight <- Stream.eval(headers.getBestHeight)
        txsWithHeights = chunk.map(tx => tx -> tx.numConfirmations(bestHeight))
        txInfo <- Stream.emits(TransactionInfo.unFlattenBatch(txsWithHeights.toList, ins, outs, inAssets, outAssets))
      } yield txInfo
  }
}