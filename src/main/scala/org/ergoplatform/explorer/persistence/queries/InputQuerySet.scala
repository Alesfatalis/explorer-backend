package org.ergoplatform.explorer.persistence.queries

import cats.data.NonEmptyList
import doobie.Fragments
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._
import org.ergoplatform.explorer.TxId
import org.ergoplatform.explorer.persistence.models.composite.ExtendedInput

object InputQuerySet extends QuerySet {

  import org.ergoplatform.explorer.persistence.doobieInstances._

  val tableName: String = "node_inputs"

  val fields: List[String] = List(
    "box_id",
    "tx_id",
    "proof_bytes",
    "extension"
  )

  def getAllByTxId(txId: TxId): ConnectionIO[List[ExtendedInput]] =
    sql"""
         |select distinct on (i.box_id)
         |  i.box_id,
         |  i.tx_id,
         |  i.proof_bytes,
         |  i.extension,
         |  o.value,
         |  o.tx_id,
         |  o.address
         |from node_inputs i
         |join node_outputs o on i.box_id = o.box_id
         |where i.tx_id = $txId
         |""".stripMargin.query[ExtendedInput].to[List]

  def getAllByTxIds(
    txsId: NonEmptyList[TxId]
  ): ConnectionIO[List[ExtendedInput]] = {
    val q =
      sql"""
           |select distinct on (i.box_id)
           |  i.box_id,
           |  i.tx_id,
           |  i.proof_bytes,
           |  i.extension,
           |  o.value,
           |  o.tx_id,
           |  o.address
           |from node_inputs i
           |join node_outputs o on i.box_id = o.box_id
           |where
           |""".stripMargin
    (q ++ Fragments.in(fr"i.tx_id", txsId))
      .query[ExtendedInput]
      .to[List]
  }
}
