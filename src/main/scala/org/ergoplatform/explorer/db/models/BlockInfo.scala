package org.ergoplatform.explorer.db.models

import cats.MonadError
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.ergoplatform.explorer.protocol.constants
import org.ergoplatform.explorer.protocol.models.ApiFullBlock
import org.ergoplatform.explorer.settings.ProtocolSettings
import org.ergoplatform.explorer.{Address, Id}
import org.ergoplatform.{ErgoScriptPredef, Pay2SAddress}
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.interpreter.CryptoConstants.EcPointType
import sigmastate.serialization.{GroupElementSerializer, SigmaSerializer}

import scala.util.Try

/** Entity representing `blocks_info` table.
  * Containing main fields from protocol header and full-block stats.
  */
final case class BlockInfo(
  headerId: Id,
  timestamp: Long,
  height: Int,
  difficulty: Long,
  blockSize: Long,           // block size (bytes)
  blockCoins: Long,
  blockMiningTime: Long,
  txsCount: Long,
  txsSize: Int,             // total size of all transactions in this block (bytes)
  minerAddress: Address,
  minerReward: Long,
  minerRevenue: Long,
  blockFee: Long,
  blockChainTotalSize: Long, // cumulative blockchain size including this block
  totalTxsCount: Long,
  totalCoinsIssued: Long,    // number of nERGs issued in block
  totalMiningTime: Long,
  totalFees: Long,           // total number of nERGs in block miner received as a fee
  totalMinersReward: Long,
  totalCoinsInTxs: Long      // total number of nERGs in all blocks
)

object BlockInfo {

  def fromApi[F[_]: MonadError[*[_], Throwable]](apiBlock: ApiFullBlock, parentBlockOpt: Option[BlockInfo])(
    protocolSettings: ProtocolSettings
  ): F[BlockInfo] =
    minerRewardAddress(apiBlock)(protocolSettings).map { minerAddress =>
      val (reward, fee) = minerRewardAndFee(apiBlock)(protocolSettings)
      val coinBaseValue = reward + fee
      val blockCoins = apiBlock.transactions.transactions
        .flatMap(_.outputs)
        .map(_.value)
        .sum - coinBaseValue
      val miningTime = apiBlock.header.timestamp - parentBlockOpt
        .map(_.timestamp)
        .getOrElse(0L)

      BlockInfo(
        headerId   = apiBlock.header.id,
        timestamp  = apiBlock.header.timestamp,
        height     = apiBlock.header.height,
        difficulty = apiBlock.header.difficulty.value.toLong,
        blockSize  = apiBlock.size,
        blockCoins = blockCoins,
        blockMiningTime = apiBlock.header.timestamp - parentBlockOpt
          .map(_.timestamp)
          .getOrElse(0L),
        txsCount     = apiBlock.transactions.transactions.length.toLong,
        txsSize      = apiBlock.transactions.transactions.map(_.size).sum,
        minerAddress = minerAddress,
        minerReward  = reward,
        minerRevenue = reward + fee,
        blockFee     = fee,
        blockChainTotalSize = parentBlockOpt
          .map(_.blockChainTotalSize)
          .getOrElse(0L) + apiBlock.size,
        totalTxsCount = apiBlock.transactions.transactions.length.toLong + parentBlockOpt
          .map(_.totalTxsCount)
          .getOrElse(0L),
        totalCoinsIssued =
          protocolSettings.emission.issuedCoinsAfterHeight(apiBlock.header.height.toLong),
        totalMiningTime   = parentBlockOpt.map(_.totalMiningTime).getOrElse(0L) + miningTime,
        totalFees         = parentBlockOpt.map(_.totalFees).getOrElse(0L) + fee,
        totalMinersReward = parentBlockOpt.map(_.totalMinersReward).getOrElse(0L) + reward,
        totalCoinsInTxs   = parentBlockOpt.map(_.totalCoinsInTxs).getOrElse(0L) + blockCoins
      )
    }

  private def minerRewardAddress[F[_]: MonadError[*[_], Throwable]](
    apiBlock: ApiFullBlock
  )(protocolSettings: ProtocolSettings): F[Address] =
    Base16
      .decode(apiBlock.header.minerPk.unwrapped)
      .flatMap { bytes =>
        Try(GroupElementSerializer.parse(SigmaSerializer.startReader(bytes)))
      }
      .fold[F[EcPointType]](_.raiseError, _.pure[F])
      .flatMap { x =>
        val minerPk = ProveDlog(x)
        val rewardScript =
          ErgoScriptPredef.rewardOutputScript(
            protocolSettings.monetary.minerRewardDelay,
            minerPk
          )
        val addressStr =
          Pay2SAddress(rewardScript)(protocolSettings.addressEncoder).toString
        Address.fromString(addressStr)
      }

  private def minerRewardAndFee(
    apiBlock: ApiFullBlock
  )(protocolSettings: ProtocolSettings): (Long, Long) = {
    val emission = protocolSettings.emission.emissionAtHeight(apiBlock.header.height.toLong)
    val reward   = math.min(constants.TeamTreasuryThreshold, emission)
    val fee = apiBlock.transactions.transactions
      .flatMap(_.outputs)
      .filter(_.ergoTree.unwrapped == constants.FeePropositionScriptHex)
      .map(_.value)
      .sum
    (reward, fee)
  }
}