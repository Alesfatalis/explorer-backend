package org.ergoplatform.explorer.db.models

import org.ergoplatform.explorer.{BoxId, BlockId, TokenId}

/** Represents `node_assets` table.
  */
final case class Asset(
                        tokenId: TokenId,
                        boxId: BoxId,
                        headerId: BlockId,
                        index: Int,
                        amount: Long
)
