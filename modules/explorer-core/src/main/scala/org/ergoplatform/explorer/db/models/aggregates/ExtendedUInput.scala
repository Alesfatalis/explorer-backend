package org.ergoplatform.explorer.db.models.aggregates

import io.circe.Json
import org.ergoplatform.explorer.db.models.UInput
import org.ergoplatform.explorer.{Address, BlockId, ErgoTree, TxId}

/** Unconfirmed input entity enriched with a data from corresponding output.
  */
final case class ExtendedUInput(
  input: UInput,
  value: Long,
  outputTxId: TxId,
  outputIndex: Int,
  outputBlockId: Option[BlockId],
  ergoTree: ErgoTree,
  address: Address,
  additionalRegisters: Json
)
