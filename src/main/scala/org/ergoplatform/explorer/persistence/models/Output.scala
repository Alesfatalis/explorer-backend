package org.ergoplatform.explorer.persistence.models

import io.circe.Json
import org.ergoplatform.explorer.{Address, BoxId, HexString, TxId}

final case class Output(
  boxId: BoxId,
  txId: TxId,
  value: Long,
  creationHeight: Int,
  index: Int,
  ergoTree: HexString,
  address: Address,
  additionalRegisters: Json,
  timestamp: Long
)