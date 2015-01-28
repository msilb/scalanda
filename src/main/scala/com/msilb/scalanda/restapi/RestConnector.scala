package com.msilb.scalanda.restapi

import java.time.ZonedDateTime

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.msilb.scalanda.common.Environment
import com.msilb.scalanda.common.Environment.SandBox
import com.msilb.scalanda.common.util.DateUtils._
import com.msilb.scalanda.common.util.NumberUtils._
import com.msilb.scalanda.restapi.RestConnector.Request._
import com.msilb.scalanda.restapi.RestConnector.Response
import com.msilb.scalanda.restapi.RestConnector.Response._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri.Query
import spray.http.{FormData, HttpHeaders, OAuth2BearerToken, Uri}
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

    case class CancelOrderRequest(orderId: Int) extends Request

    case class CloseTradeRequest(tradeId: Int) extends Request

    case class CreateOrderRequest(instrument: String, units: Int, side: String, `type`: String, expiry: Option[ZonedDateTime] = None, price: Option[Double] = None, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None) extends Request

    case class ModifyTradeRequest(id: Int, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None, trailingStop: Option[Double] = None) extends Request

    case class ModifyOrderRequest(id: Int, units: Option[Int] = None, price: Option[Double] = None, expiry: Option[ZonedDateTime] = None, lowerBound: Option[Double] = None, upperBound: Option[Double] = None, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None, trailingStop: Option[Double] = None) extends Request

    case class GetTradesRequest(instrument: String, count: Int) extends Request

    case class GetOrdersRequest(instrument: String, count: Int) extends Request

    case class GetCandlesRequest(instrument: String, count: Int, granularity: String, candleFormat: String) extends Request

    case class ClosePositionRequest(instrument: String) extends Request

    case object CreateTestAccountRequest extends Request

    case class GetInstrumentsRequest(fields: Option[Seq[String]] = None, instruments: Option[Seq[String]] = None) extends Request

    case class GetCurrentPricesRequest(instruments: Seq[String], since: Option[ZonedDateTime] = None) extends Request

  }

  sealed trait Response

  object Response {

    case class CancelOrderResponse(id: Int, instrument: String, units: Int, side: String, price: Double, time: ZonedDateTime) extends Response

    case class CloseTradeResponse(id: Int, instrument: String, profit: Double, side: String, price: Double, time: ZonedDateTime) extends Response

    case class OrderOpened(id: Int, units: Int, side: String, expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, takeProfit: Double, stopLoss: Double, trailingStop: Double)

    case class TradeOpened(id: Int, units: Int, side: String, takeProfit: Double, stopLoss: Double, trailingStop: Double)

    case class CreateOrderResponse(instrument: String, time: ZonedDateTime, price: Double, orderOpened: Option[OrderOpened], tradeOpened: Option[TradeOpened]) extends Response

    case class ModifyTradeResponse(id: Int, instrument: String, units: Int, side: String, time: ZonedDateTime, price: Double, takeProfit: Option[Double], stopLoss: Option[Double], trailingStop: Option[Double], trailingAmount: Option[Double]) extends Response

    case class ModifyOrderResponse(id: Int, instrument: String, units: Int, side: String, `type`: String, time: ZonedDateTime, price: Double, takeProfit: Option[Double], stopLoss: Option[Double], expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, trailingStop: Option[Double]) extends Response

    case class Trade(id: Int, units: Int, side: String, instrument: String, time: ZonedDateTime, price: Double, takeProfit: Double, stopLoss: Double, trailingStop: Double, trailingAmount: Double)

    case class TradeResponse(trades: Seq[Trade]) extends Response

    case class Order(id: Int, instrument: String, units: Int, side: String, `type`: String, time: ZonedDateTime, price: Double, takeProfit: Double, stopLoss: Double, expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, trailingStop: Double)

    case class OrderResponse(orders: Seq[Order]) extends Response

    case class Candle(time: ZonedDateTime, openBid: Double, openAsk: Double, highBid: Double, highAsk: Double, lowBid: Double, lowAsk: Double, closeBid: Double, closeAsk: Double, volume: Int, complete: Boolean)

    case class CandleResponse(instrument: String, granularity: String, candles: Seq[Candle]) extends Response

    case class ClosePositionResponse(ids: Seq[Int], instrument: String, totalUnits: Int, price: Double) extends Response

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

    object OandaJsonProtocol extends DefaultJsonProtocol {
      implicit val cancelOrderResponseFormat = jsonFormat6(CancelOrderResponse)
      implicit val orderOpenedFormat = jsonFormat9(OrderOpened)
      implicit val tradeOpenedFormat = jsonFormat6(TradeOpened)
      implicit val createOrderResponseFormat = jsonFormat5(CreateOrderResponse)
      implicit val modifyTradeResponseFormat = jsonFormat10(ModifyTradeResponse)
      implicit val tradeFmt = jsonFormat10(Trade)
      implicit val tradeResponseFmt = jsonFormat1(TradeResponse)
      implicit val orderFormat = jsonFormat13(Order)
      implicit val orderResponseFormat = jsonFormat1(OrderResponse)
      implicit val candleFmt = jsonFormat11(Candle)
      implicit val candleResponseFmt = jsonFormat3(CandleResponse)
      implicit val closePositionResponseFmt = jsonFormat4(ClosePositionResponse)
      implicit val closeTradeResponseFmt = jsonFormat6(CloseTradeResponse)
      implicit val modifyOrderResponseFmt = jsonFormat13(ModifyOrderResponse)
      implicit val instrumentFmt = jsonFormat9(Instrument)
      implicit val getInstrumentsResponseFmt = jsonFormat1(GetInstrumentsResponse)
      implicit val priceFmt = jsonFormat5(Price)
      implicit val getCurrentPricesResponseFmt = jsonFormat1(GetCurrentPricesResponse)
    }

  }

}

class RestConnector(env: Environment, authTokenOpt: Option[String], accountId: Int) extends Actor with ActorLogging {

  import com.msilb.scalanda.restapi.RestConnector.Response.OandaJsonProtocol._
  import context._

  implicit val timeout = Timeout(5.seconds)

  private def pipeline[T <: Response](implicit unmarshaller: FromResponseUnmarshaller[T]) =
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

  private def handleRequest[T <: Response](f: Future[T]) = {
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
    case req: CancelOrderRequest =>
      log.info("Canceling order with id {}", req.orderId)
      handleRequest(pipeline[CancelOrderResponse].flatMap(_(Delete(s"/v1/accounts/$accountId/orders/${req.orderId}"))))
    case req: CreateOrderRequest =>
      log.info("Creating new order: {}", req)
      val data = FormData(
        Map(
          "instrument" -> req.instrument,
          "units" -> req.units.toString,
          "side" -> req.side,
          "type" -> req.`type`
        ) ++ req.price.map(p => "price" -> decimalFormatter.format(p))
          ++ req.stopLoss.map(sl => "stopLoss" -> decimalFormatter.format(sl))
          ++ req.takeProfit.map(tp => "takeProfit" -> decimalFormatter.format(tp))
          ++ req.expiry.map(ex => "expiry" -> dateTimeFormatter.format(ex))
      )
      handleRequest(pipeline[CreateOrderResponse].flatMap(_(Post(s"/v1/accounts/$accountId/orders", data))))
    case req: ModifyOrderRequest =>
      log.info("Modifying order: {}", req)
      val data = FormData(Map() ++ req.units.map("units" -> _.toString) ++ req.price.map("price" -> _.toString))
      handleRequest(pipeline[ModifyOrderResponse].flatMap(_(Patch(s"/v1/accounts/$accountId/orders/${req.id}", data))))
    case req: ModifyTradeRequest =>
      log.info("Modifying trade: {}", req)
      val data = FormData(Map()
        ++ req.takeProfit.map(tp => "takeProfit" -> decimalFormatter.format(tp))
        ++ req.stopLoss.map(sl => "stopLoss" -> decimalFormatter.format(sl))
        ++ req.trailingStop.map(ts => "trailingStop" -> decimalFormatter.format(ts)))
      handleRequest(pipeline[ModifyTradeResponse].flatMap(_(Patch(s"/v1/accounts/$accountId/trades/${req.id}", data))))
    case req: GetTradesRequest =>
      log.info("Getting open trades: {}", req)
      handleRequest(pipeline[TradeResponse].flatMap(_(Get(s"/v1/accounts/$accountId/trades?instrument=${req.instrument}&count=${req.count}"))))
    case req: GetOrdersRequest =>
      log.info("Getting open orders: {}", req)
      handleRequest(pipeline[OrderResponse].flatMap(_(Get(s"/v1/accounts/$accountId/orders?instrument=${req.instrument}&count=${req.count}"))))
    case req: GetCandlesRequest =>
      log.info("Getting historical candles: {}", req)
      handleRequest(pipeline[CandleResponse].flatMap(_(Get(s"/v1/candles?instrument=${req.instrument}&count=${req.count}&candleFormat=${req.candleFormat}&granularity=${req.granularity}"))))
    case req: ClosePositionRequest =>
      log.info("Closing position: {}", req)
      handleRequest(pipeline[ClosePositionResponse].flatMap(_(Delete(s"/v1/accounts/$accountId/positions/${req.instrument}"))))
    case req: CloseTradeRequest =>
      log.info("Closing trade: {}", req)
      handleRequest(pipeline[CloseTradeResponse].flatMap(_(Delete(s"/v1/accounts/$accountId/trades/${req.tradeId}"))))
    case req: GetInstrumentsRequest =>
      log.info("Getting instruments: {}", req)
      val uri = Uri("/v1/instruments").withQuery(
        Query.asBodyData(
          Seq(
            ("accountId", accountId.toString)
          ) ++ req.fields.map(fields => Seq(("fields", fields.mkString(",")))).getOrElse(Nil)
            ++ req.instruments.map(instruments => Seq(("instruments", instruments.mkString(",")))).getOrElse(Nil)
        )
      )
      handleRequest(pipeline[GetInstrumentsResponse].flatMap(_(Get(uri))))
    case req: GetCurrentPricesRequest =>
      log.info("Getting current prices: {}", req)
      val uri = Uri("/v1/prices").withQuery(
        Query.asBodyData(
          Seq(
            ("instruments", req.instruments.mkString(","))
          ) ++ req.since.map(since => Seq(("since", dateTimeFormatter.format(since)))).getOrElse(Nil)
        )
      )
      handleRequest(pipeline[GetCurrentPricesResponse].flatMap(_(Get(uri))))
  }
}
