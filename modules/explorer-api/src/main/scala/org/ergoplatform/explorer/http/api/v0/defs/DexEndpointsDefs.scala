package org.ergoplatform.explorer.http.api.v0.defs

import org.ergoplatform.explorer.TokenId
import org.ergoplatform.explorer.http.api.ApiErr
import org.ergoplatform.explorer.http.api.commonDirectives._
import org.ergoplatform.explorer.http.api.models.Paging
import org.ergoplatform.explorer.http.api.v0.models.{DexBuyOrderInfo, DexSellOrderInfo}
import sttp.tapir._
import sttp.tapir.json.circe._

object DexEndpointsDefs {

  private val PathPrefix = "dex"

  def endpoints: List[Endpoint[_, _, _, _]] =
    getUnspentSellOrdersDef :: getUnspentBuyOrdersDef :: Nil

  def getUnspentSellOrdersDef
    : Endpoint[(TokenId, Paging), ApiErr, List[DexSellOrderInfo], Nothing] =
    baseEndpointDef
      .in(PathPrefix / "tokens" / path[TokenId] / "unspentSellOrders")
      .in(paging)
      .out(jsonBody[List[DexSellOrderInfo]])

  def getUnspentBuyOrdersDef
    : Endpoint[(TokenId, Paging), ApiErr, List[DexBuyOrderInfo], Nothing] =
    baseEndpointDef
      .in(PathPrefix / "tokens" / path[TokenId] / "unspentBuyOrders")
      .in(paging)
      .out(jsonBody[List[DexBuyOrderInfo]])
}
