package com.msilb.scalanda.restapi

import java.time.ZonedDateTime

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.msilb.scalanda.common.Environment
import com.msilb.scalanda.common.Environment.SandBox
import com.msilb.scalanda.common.model.Candle.CandleJsonProtocol._
import com.msilb.scalanda.common.model.Candle.{BidAskBasedCandle, MidPointBasedCandle}
import com.msilb.scalanda.common.model.CandleFormat.MidPoint
import com.msilb.scalanda.common.model._
import com.msilb.scalanda.common.util.DateUtils._
import com.msilb.scalanda.common.util.NumberUtils._
import com.msilb.scalanda.restapi.RestConnector.Request._
import com.msilb.scalanda.restapi.RestConnector.Response
import com.msilb.scalanda.restapi.RestConnector.Response._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri.Query
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RestConnector {

  def props(env: Environment = SandBox, authToken: Option[String] = None, accountId: Int) = Props(new RestConnector(env, authToken, accountId))

  sealed trait Request

  object Request {

    // RATES

    case class GetInstrumentsRequest(fields: Option[Seq[InstrumentField]] = None,
                                     instruments: Option[Seq[String]] = None) extends Request

    case class GetCurrentPricesRequest(instruments: Seq[String],
                                       since: Option[ZonedDateTime] = None) extends Request

    case class GetCandlesRequest(instrument: String,
                                 granularity: Option[Granularity] = None,
                                 count: Option[Int] = None,
                                 start: Option[ZonedDateTime] = None,
                                 end: Option[ZonedDateTime] = None,
                                 candleFormat: Option[CandleFormat] = None,
                                 includeFirst: Option[Boolean] = None,
                                 dailyAlignment: Option[Byte] = None,
                                 alignmentTimeZone: Option[AlignmentTimeZone] = None,
                                 weeklyAlignment: Option[WeeklyAlignment] = None) extends Request

    // ACCOUNTS

    case class GetAccountsRequest(username: Option[String] = None) extends Request

    case class CreateTestAccountRequest(currency: Option[String] = None) extends Request

    case class GetAccountInformationRequest(accountId: Int) extends Request

    // ORDERS

    case class GetOrdersRequest(maxId: Option[Int] = None,
                                count: Option[Int] = None,
                                instrument: Option[String] = None,
                                ids: Option[List[Int]] = None) extends Request

    case class CreateOrderRequest(instrument: String,
                                  units: Int,
                                  side: Side,
                                  typ: OrderType,
                                  expiry: Option[ZonedDateTime] = None,
                                  price: Option[Double] = None,
                                  lowerBound: Option[Double] = None,
                                  upperBound: Option[Double] = None,
                                  stopLoss: Option[Double] = None,
                                  takeProfit: Option[Double] = None,
                                  trailingStop: Option[Double] = None) extends Request

    case class GetOrderInformationRequest(orderId: Int) extends Request

    case class ModifyOrderRequest(id: Int,
                                  units: Option[Int] = None,
                                  price: Option[Double] = None,
                                  expiry: Option[ZonedDateTime] = None,
                                  lowerBound: Option[Double] = None,
                                  upperBound: Option[Double] = None,
                                  stopLoss: Option[Double] = None,
                                  takeProfit: Option[Double] = None,
                                  trailingStop: Option[Double] = None) extends Request

    case class CloseOrderRequest(orderId: Int) extends Request

    // TRADES

    case class GetOpenTradesRequest(maxId: Option[Int] = None,
                                    count: Option[Int] = None,
                                    instrument: Option[String] = None,
                                    ids: Option[List[Int]] = None) extends Request

    case class GetTradeInformationRequest(tradeId: Int) extends Request

    case class ModifyTradeRequest(id: Int, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None, trailingStop: Option[Double] = None) extends Request

    case class CloseTradeRequest(tradeId: Int) extends Request

    // POSITIONS

    case object GetOpenPositionsRequest extends Request

    case class GetPositionForInstrumentRequest(instrument: String) extends Request

    case class ClosePositionRequest(instrument: String) extends Request

  }

  sealed trait Response

  object Response {

    case class Instrument(instrument: String,
                          displayName: Option[String],
                          pip: Option[String],
                          precision: Option[String],
                          maxTradeUnits: Option[Double],
                          maxTrailingStop: Option[Double],
                          minTrailingStop: Option[Double],
                          marginRate: Option[Double],
                          halted: Option[Boolean])

    case class GetInstrumentsResponse(instruments: Seq[Instrument]) extends Response

    case class Price(instrument: String,
                     time: ZonedDateTime,
                     bid: Double,
                     ask: Double,
                     status: Option[String])

    case class GetCurrentPricesResponse(prices: Seq[Price]) extends Response

    case class CandleResponse[+T <: Candle](instrument: String, granularity: Granularity, candles: Seq[T]) extends Response

    case class Account(accountId: Int, accountName: String, accountCurrency: String, marginRate: Double)

    case class GetAccountsResponse(accounts: Seq[Account]) extends Response

    case class CreateTestAccountResponse(username: String, password: String, accountId: Int) extends Response

    case class GetAccountInformationResponse(accountId: Int,
                                             accountName: String,
                                             balance: BigDecimal,
                                             unrealizedPl: BigDecimal,
                                             realizedPl: BigDecimal,
                                             marginUsed: BigDecimal,
                                             marginAvail: BigDecimal,
                                             openTrades: Int,
                                             openOrders: Int,
                                             marginRate: Double,
                                             accountCurrency: String) extends Response

    case class OrderOpened(id: Int, units: Int, side: Side, expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, takeProfit: Double, stopLoss: Double, trailingStop: Double)

    case class TradeOpened(id: Int, units: Int, side: Side, takeProfit: Double, stopLoss: Double, trailingStop: Double)

    case class OrderResponse(id: Int,
                             instrument: String,
                             units: Int,
                             side: Side,
                             typ: OrderType,
                             time: ZonedDateTime,
                             price: Double,
                             takeProfit: Double,
                             stopLoss: Double,
                             expiry: ZonedDateTime,
                             upperBound: Double,
                             lowerBound: Double,
                             trailingStop: Double) extends Response

    case class GetOrdersResponse(orders: Seq[OrderResponse]) extends Response

    case class CreateOrderResponse(instrument: String, time: ZonedDateTime, price: Double, orderOpened: Option[OrderOpened], tradeOpened: Option[TradeOpened]) extends Response

    case class CloseOrderResponse(id: Int, instrument: String, units: Int, side: Side, price: Double, time: ZonedDateTime) extends Response

    case class TradeResponse(id: Int,
                             units: Int,
                             side: Side,
                             instrument: String,
                             time: ZonedDateTime,
                             price: Double,
                             takeProfit: Double,
                             stopLoss: Double,
                             trailingStop: Double,
                             trailingAmount: Double) extends Response

    case class GetOpenTradesResponse(trades: Seq[TradeResponse]) extends Response

    case class CloseTradeResponse(id: Int,
                                  price: Double,
                                  instrument: String,
                                  profit: Double,
                                  side: Side,
                                  time: ZonedDateTime) extends Response

    case class PositionResponse(instrument: String, units: Int, side: Side, avgPrice: Double) extends Response

    case class GetOpenPositionsResponse(positions: Seq[PositionResponse]) extends Response

    case class ClosePositionResponse(ids: Seq[Int], instrument: String, totalUnits: Int, price: Double) extends Response

    object OandaJsonProtocol extends DefaultJsonProtocol {
      implicit val closeOrderResponseFormat = jsonFormat6(CloseOrderResponse)
      implicit val orderOpenedFormat = jsonFormat9(OrderOpened)
      implicit val tradeOpenedFormat = jsonFormat6(TradeOpened)
      implicit val createOrderResponseFormat = jsonFormat5(CreateOrderResponse)
      implicit val tradeResponseFmt = jsonFormat10(TradeResponse)
      implicit val getOpenTradesResponseFmt = jsonFormat1(GetOpenTradesResponse)
      implicit val orderResponseFormat = jsonFormat(OrderResponse,
        "id",
        "instrument",
        "units",
        "side",
        "type",
        "time",
        "price",
        "takeProfit",
        "stopLoss",
        "expiry",
        "upperBound",
        "lowerBound",
        "trailingStop"
      )
      implicit val getOrdersResponseFmt = jsonFormat1(GetOrdersResponse)
      implicit val midPointBasedCandleResponseFmt = jsonFormat3(CandleResponse[MidPointBasedCandle])
      implicit val bidAskBasedCandleResponseFmt = jsonFormat3(CandleResponse[BidAskBasedCandle])
      implicit val closePositionResponseFmt = jsonFormat4(ClosePositionResponse)
      implicit val closeTradeResponseFmt = jsonFormat6(CloseTradeResponse)
      implicit val instrumentFmt = jsonFormat9(Instrument)
      implicit val getInstrumentsResponseFmt = jsonFormat1(GetInstrumentsResponse)
      implicit val priceFmt = jsonFormat5(Price)
      implicit val getCurrentPricesResponseFmt = jsonFormat1(GetCurrentPricesResponse)
      implicit val accountFmt = jsonFormat4(Account)
      implicit val getAccountsResponseFmt = jsonFormat1(GetAccountsResponse)
      implicit val createTestAccountResponseFmt = jsonFormat3(CreateTestAccountResponse)
      implicit val getAccountInformationResponseFmt = jsonFormat11(GetAccountInformationResponse)
      implicit val positionResponseFmt = jsonFormat4(PositionResponse)
      implicit val getOpenPositionsResponseFmt = jsonFormat1(GetOpenPositionsResponse)
    }

  }

}

class RestConnector(env: Environment, authTokenOpt: Option[String], accountId: Int) extends Actor with ActorLogging {

  import com.msilb.scalanda.restapi.RestConnector.Response.OandaJsonProtocol._
  import context._

  implicit val timeout = Timeout(5.seconds)

  private[this] def pipelineFuture[T <: Response](implicit unmarshaller: FromResponseUnmarshaller[T]): Future[HttpRequest => Future[T]] =
    for {
      Http.HostConnectorInfo(connector, _) <-
      IO(Http) ? Http.HostConnectorSetup(
        host = env.restApiUrl(),
        port = if (env.authenticationRequired()) 443 else 80,
        sslEncryption = env.authenticationRequired(),
        defaultHeaders = authTokenOpt.map(authToken => List(HttpHeaders.Authorization(OAuth2BearerToken(authToken)))).getOrElse(Nil)
      )
    } yield authTokenOpt match {
      case Some(authToken) => addCredentials(OAuth2BearerToken(authToken)) ~> sendReceive(connector) ~> unmarshal[T]
      case None => sendReceive(connector) ~> unmarshal[T]
    }

  private[this] def handleRequest[T <: Response](f: Future[T]) = {
    val requester = sender()
    f onComplete {
      case Success(response) =>
        log.debug("Received response from Oanda REST API: {}", response)
        requester ! response
      case Failure(t) =>
        log.error(t, "Error occurred while issuing HTTP request to Oanda REST API")
    }
  }

  override def receive = {

    case req: GetInstrumentsRequest =>
      log.info("Getting instruments: {}", req)
      val uri = Uri("/v1/instruments").withQuery(
        Query.asBodyData(
          Seq(
            Some(("accountId", accountId.toString)),
            req.fields.map(fields => ("fields", fields.mkString(","))),
            req.instruments.map(instruments => ("instruments", instruments.mkString(",")))
          ).flatten
        )
      )
      handleRequest(pipelineFuture[GetInstrumentsResponse].flatMap(_(Get(uri))))
    case req: GetCurrentPricesRequest =>
      log.info("Getting current prices: {}", req)
      val uri = Uri("/v1/prices").withQuery(
        Query.asBodyData(
          Seq(
            Some(("instruments", req.instruments.mkString(","))),
            req.since.map(since => ("since", dateTimeFormatter.format(since)))
          ).flatten
        )
      )
      handleRequest(pipelineFuture[GetCurrentPricesResponse].flatMap(_(Get(uri))))
    case req: GetCandlesRequest =>
      log.info("Getting historical candles: {}", req)
      val candlePipelineFuture: Future[HttpRequest => Future[CandleResponse[Candle]]] = if (req.candleFormat.contains(MidPoint)) pipelineFuture[CandleResponse[MidPointBasedCandle]] else pipelineFuture[CandleResponse[BidAskBasedCandle]]
      val uri = Uri("/v1/candles").withQuery(
        Query.asBodyData(
          Seq(
            Some(("instrument", req.instrument)),
            req.granularity.map(g => ("granularity", g.toString)),
            req.count.map(c => ("count", c.toString)),
            req.start.map(s => ("start", dateTimeFormatter.format(s))),
            req.end.map(e => ("end", dateTimeFormatter.format(e))),
            req.candleFormat.map(c => ("candleFormat", c.toString)),
            req.includeFirst.map(i => ("includeFirst", i.toString)),
            req.dailyAlignment.map(d => ("dailyAlignment", d.toString)),
            req.alignmentTimeZone.map(a => ("alignmentTimeZone", a.toString)),
            req.weeklyAlignment.map(w => ("weeklyAlignment", w.toString))
          ).flatten
        )
      )
      handleRequest(candlePipelineFuture.flatMap(_(Get(uri))))

    case req: GetAccountsRequest =>
      log.info("Getting accounts: {}", req)
      val uri = Uri("/v1/accounts").withQuery(
        Query.asBodyData(
          Seq(req.username.map(u => ("username", u))).flatten
        )
      )
      handleRequest(pipelineFuture[GetAccountsResponse].flatMap(_(Get(uri))))
    case req: CreateTestAccountRequest =>
      log.info("Creating test account: {}", req)
      val data = FormData(req.currency.map(c => Seq(("currency", c))).getOrElse(Nil))
      handleRequest(pipelineFuture[CreateTestAccountResponse].flatMap(_(Post(s"/v1/accounts/", data))))
    case req: GetAccountInformationRequest =>
      log.info("Getting account information: {}", req)
      val uri = s"/v1/accounts/${req.accountId}"
      handleRequest(pipelineFuture[GetAccountInformationResponse].flatMap(_(Get(uri))))

    case req: GetOrdersRequest =>
      log.info("Getting open orders: {}", req)
      val uri = Uri(s"/v1/accounts/$accountId/orders").withQuery(
        Query.asBodyData(
          Seq(
            req.maxId.map(maxId => ("maxId", maxId.toString)),
            req.count.map(count => ("count", count.toString)),
            req.instrument.map(instrument => ("instrument", instrument)),
            req.ids.map(ids => ("ids", ids.mkString(",")))
          ).flatten
        )
      )
      handleRequest(pipelineFuture[GetOrdersResponse].flatMap(_(Get(uri))))
    case req: CreateOrderRequest =>
      log.info("Creating new order: {}", req)
      val data = FormData(
        Map(
          "instrument" -> req.instrument,
          "units" -> req.units.toString,
          "side" -> req.side.toString,
          "type" -> req.typ.toString
        ) ++ req.expiry.map(e => "expiry" -> dateTimeFormatter.format(e))
          ++ req.price.map("price" -> _.toString)
          ++ req.lowerBound.map("lowerBound" -> _.toString)
          ++ req.upperBound.map("upperBound" -> _.toString)
          ++ req.stopLoss.map("stopLoss" -> _.toString)
          ++ req.takeProfit.map("takeProfit" -> _.toString)
          ++ req.trailingStop.map("trailingStop" -> _.toString)
      )
      handleRequest(pipelineFuture[CreateOrderResponse].flatMap(_(Post(s"/v1/accounts/$accountId/orders", data))))
    case req: GetOrderInformationRequest =>
      log.info("Getting information for an order: {}", req)
      handleRequest(pipelineFuture[OrderResponse].flatMap(_(Get(s"/v1/accounts/$accountId/orders/${req.orderId}"))))
    case req: ModifyOrderRequest =>
      log.info("Modifying order: {}", req)
      val data = FormData(
        Map() ++ req.units.map("units" -> _.toString)
          ++ req.price.map("price" -> _.toString)
          ++ req.expiry.map(e => "expiry" -> dateTimeFormatter.format(e))
          ++ req.lowerBound.map("lowerBound" -> _.toString)
          ++ req.upperBound.map("upperBound" -> _.toString)
          ++ req.stopLoss.map("stopLoss" -> _.toString)
          ++ req.takeProfit.map("takeProfit" -> _.toString)
          ++ req.trailingStop.map("trailingStop" -> _.toString)
      )
      handleRequest(pipelineFuture[OrderResponse].flatMap(_(Patch(s"/v1/accounts/$accountId/orders/${req.id}", data))))
    case req: CloseOrderRequest =>
      log.info("Closing order {}", req)
      handleRequest(pipelineFuture[CloseOrderResponse].flatMap(_(Delete(s"/v1/accounts/$accountId/orders/${req.orderId}"))))

    case req: GetOpenTradesRequest =>
      log.info("Getting open trades: {}", req)
      val uri = Uri(s"/v1/accounts/$accountId/trades").withQuery(
        Query.asBodyData(
          Seq(
            req.maxId.map(maxId => ("maxId", maxId.toString)),
            req.count.map(count => ("count", count.toString)),
            req.instrument.map(instrument => ("instrument", instrument)),
            req.ids.map(ids => ("ids", ids.mkString(",")))
          ).flatten
        )
      )
      handleRequest(pipelineFuture[GetOpenTradesResponse].flatMap(_(Get(uri))))
    case req: GetTradeInformationRequest =>
      log.info("Getting information for a trade: {}", req)
      handleRequest(pipelineFuture[TradeResponse].flatMap(_(Get(s"/v1/accounts/$accountId/trades/${req.tradeId}"))))
    case req: ModifyTradeRequest =>
      log.info("Modifying trade: {}", req)
      val data = FormData(
        Map() ++ req.takeProfit.map(tp => "takeProfit" -> decimalFormatter.format(tp))
          ++ req.stopLoss.map(sl => "stopLoss" -> decimalFormatter.format(sl))
          ++ req.trailingStop.map(ts => "trailingStop" -> decimalFormatter.format(ts))
      )
      handleRequest(pipelineFuture[TradeResponse].flatMap(_(Patch(s"/v1/accounts/$accountId/trades/${req.id}", data))))
    case req: CloseTradeRequest =>
      log.info("Closing trade: {}", req)
      handleRequest(pipelineFuture[CloseTradeResponse].flatMap(_(Delete(s"/v1/accounts/$accountId/trades/${req.tradeId}"))))

    case GetOpenPositionsRequest =>
      log.info("Getting open positions")
      handleRequest(pipelineFuture[GetOpenPositionsResponse].flatMap(_(Get(s"/v1/accounts/$accountId/positions"))))
    case req: GetPositionForInstrumentRequest =>
      log.info("Getting open positions for instrument: {}", req)
      handleRequest(pipelineFuture[PositionResponse].flatMap(_(Get(s"/v1/accounts/$accountId/positions/${req.instrument}"))))
    case req: ClosePositionRequest =>
      log.info("Closing position: {}", req)
      handleRequest(pipelineFuture[ClosePositionResponse].flatMap(_(Delete(s"/v1/accounts/$accountId/positions/${req.instrument}"))))
  }
}
