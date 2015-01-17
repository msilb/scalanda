package com.msilb.scalanda.restapi

import java.time.ZonedDateTime

import akka.actor._
import akka.util.Timeout
import com.msilb.scalanda.common.Environment
import com.msilb.scalanda.common.Environment.SandBox
import com.msilb.scalanda.common.util.DateUtils._
import com.msilb.scalanda.common.util.NumberUtils._
import com.msilb.scalanda.restapi.OandaConnector.Request._
import com.msilb.scalanda.restapi.OandaConnector.Response
import com.msilb.scalanda.restapi.OandaConnector.Response._
import spray.client.pipelining._
import spray.http.{FormData, OAuth2BearerToken}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object OandaConnector {

  def props(env: Environment = SandBox, authToken: Option[String] = None, accountId: Int = 1234567) = Props(new OandaConnector(env, authToken, accountId))

  sealed trait Request

  object Request {

    case class ConnectRequest(environment: Environment = SandBox, authToken: Option[String] = None) extends Request

    case class CancelOrderRequest(orderId: Int) extends Request

    case class CloseTradeRequest(tradeId: Int) extends Request

    case class CreateOrderRequest(instrument: String, units: Int, side: String, `type`: String, expiry: Option[ZonedDateTime] = None, price: Option[Double] = None, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None) extends Request

    case class ModifyTradeRequest(id: Int, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None, trailingStop: Option[Double] = None) extends Request

    case class ModifyOrderRequest(id: Int, units: Option[Int] = None, price: Option[Double] = None, expiry: Option[ZonedDateTime] = None, lowerBound: Option[Double] = None, upperBound: Option[Double] = None, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None, trailingStop: Option[Double] = None) extends Request

    case class GetTradesRequest(instrument: String, count: Int) extends Request

    case class GetOrdersRequest(instrument: String, count: Int) extends Request

    case class GetCandlesRequest(instrument: String, count: Int, granularity: String, candleFormat: String) extends Request

    case class ClosePositionRequest(instrument: String) extends Request

  }

  sealed trait Response

  object Response {

    case class AuthenticationResponse()

    case class CancelOrderResponse(id: Int, instrument: String, units: Int, side: String, price: Double, time: ZonedDateTime) extends Response

    case class CloseTradeResponse(id: Int, instrument: String, profit: Double, side: String, price: Double, time: ZonedDateTime) extends Response

    case class OrderOpened(id: Int, units: Int, side: String, expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, takeProfit: Double, stopLoss: Double, trailingStop: Double)

    case class TradeOpened(id: Int, units: Int, side: String, takeProfit: Double, stopLoss: Double, trailingStop: Double)

    case class CreateOrderResponse(instrument: String, time: ZonedDateTime, price: Double, orderOpened: Option[OrderOpened], tradeOpened: Option[TradeOpened]) extends Response

    case class ModifyTradeResponse(id: Int, instrument: String, units: Int, side: String, time: ZonedDateTime, price: Double, takeProfit: Option[Double], stopLoss: Option[Double], trailingStop: Option[Double], trailingAmount: Option[Double]) extends Response

    case class ModifyOrderResponse(id: Int, instrument: String, units: Int, side: String, `type`: String, time: ZonedDateTime, price: Double, takeProfit: Option[Double], stopLoss: Option[Double], expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, trailingStop: Option[Double]) extends Response

    case class Trade(id: Int, units: Int, side: String, instrument: String, time: ZonedDateTime, price: Double, takeProfit: Double, stopLoss: Double, trailingStop: Double, trailingAmount: Double)

    case class TradeResponse(trades: List[Trade]) extends Response

    case class Order(id: Int, instrument: String, units: Int, side: String, `type`: String, time: ZonedDateTime, price: Double, takeProfit: Double, stopLoss: Double, expiry: ZonedDateTime, upperBound: Double, lowerBound: Double, trailingStop: Double)

    case class OrderResponse(orders: List[Order]) extends Response

    case class Candle(time: ZonedDateTime, openBid: Double, openAsk: Double, highBid: Double, highAsk: Double, lowBid: Double, lowAsk: Double, closeBid: Double, closeAsk: Double, volume: Int, complete: Boolean)

    case class CandleResponse(instrument: String, granularity: String, candles: List[Candle]) extends Response

    case class ClosePositionResponse(ids: List[Int], instrument: String, totalUnits: Int, price: Double) extends Response

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
    }

  }

}

class OandaConnector(env: Environment, authTokenOpt: Option[String], accountId: Int) extends Actor with ActorLogging {

  import com.msilb.scalanda.restapi.OandaConnector.Response.OandaJsonProtocol._
  import context.dispatcher

  implicit val timeout = Timeout(5.seconds)

  private def pipeline[T <: Response](implicit unmarshaller: FromResponseUnmarshaller[T]) = authTokenOpt match {
    case Some(authToken) => addCredentials(OAuth2BearerToken(authToken)) ~> sendReceive ~> unmarshal[T]
    case None => sendReceive ~> unmarshal[T]
  }

  private val cancelOrderPipeline = pipeline[CancelOrderResponse]
  private val createOrderPipeline = pipeline[CreateOrderResponse]
  private val modifyTradePipeline = pipeline[ModifyTradeResponse]
  private val getTradesPipeline = pipeline[TradeResponse]
  private val getOrdersPipeline = pipeline[OrderResponse]
  private val getCandlesPipeline = pipeline[CandleResponse]
  private val closePositionPipeline = pipeline[ClosePositionResponse]
  private val closeTradePipeline = pipeline[CloseTradeResponse]
  private val modifyOrderPipeline = pipeline[ModifyOrderResponse]

  private def handleRequest[T <: Response](f: Future[T]) = {
    val requester = sender()
    f onComplete {
      case Success(response) =>
        log.debug("Got response from Oanda: {}", response)
        requester ! response
      case Failure(t) =>
        log.error(t, "Error occurred when issuing HTTP request to Oanda REST API")
    }
  }

  override def receive = {
    case req: CancelOrderRequest =>
      log.info("Canceling order: {}", req)
      handleRequest(cancelOrderPipeline(Delete(s"${env.restApiUrl()}/v1/accounts/$accountId/orders/${req.orderId}")))
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
      handleRequest(createOrderPipeline(Post(s"${env.restApiUrl()}/v1/accounts/$accountId/orders", data)))
    case req: ModifyOrderRequest =>
      log.info("Modifying order: {}", req)
      val data = FormData(Map() ++ req.units.map("units" -> _.toString) ++ req.price.map("price" -> _.toString))
      handleRequest(modifyOrderPipeline(Patch(s"${env.restApiUrl()}/v1/accounts/$accountId/orders/${req.id}", data)))
    case req: ModifyTradeRequest =>
      log.info("Modifying trade: {}", req)
      val data = FormData(Map()
        ++ req.takeProfit.map(tp => "takeProfit" -> decimalFormatter.format(tp))
        ++ req.stopLoss.map(sl => "stopLoss" -> decimalFormatter.format(sl))
        ++ req.trailingStop.map(ts => "trailingStop" -> decimalFormatter.format(ts)))
      handleRequest(modifyTradePipeline(Patch(s"${env.restApiUrl()}/v1/accounts/$accountId/trades/${req.id}", data)))
    case req: GetTradesRequest =>
      log.info("Getting open trades: {}", req)
      handleRequest(getTradesPipeline(Get(s"${env.restApiUrl()}/v1/accounts/$accountId/trades?instrument=${req.instrument}&count=${req.count}")))
    case req: GetOrdersRequest =>
      log.info("Getting open orders: {}", req)
      handleRequest(getOrdersPipeline(Get(s"${env.restApiUrl()}/v1/accounts/$accountId/orders?instrument=${req.instrument}&count=${req.count}")))
    case req: GetCandlesRequest =>
      log.debug("Getting historical candles: {}", req)
      handleRequest(getCandlesPipeline(Get(s"${env.restApiUrl()}/v1/candles?instrument=${req.instrument}&count=${req.count}&candleFormat=${req.candleFormat}&granularity=${req.granularity}")))
    case req: ClosePositionRequest =>
      log.info("Closing position: {}", req)
      handleRequest(closePositionPipeline(Delete(s"${env.restApiUrl()}/v1/accounts/$accountId/positions/${req.instrument}")))
    case req: CloseTradeRequest =>
      log.info("Closing trade: {}", req)
      handleRequest(closeTradePipeline(Delete(s"${env.restApiUrl()}/v1/accounts/$accountId/trades/${req.tradeId}")))
  }
}
