package com.msilb.scalanda.restapi

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.common.model.Side.Buy
import com.msilb.scalanda.common.model.Transaction
import com.msilb.scalanda.restapi.Request._
import com.msilb.scalanda.restapi.Response._
import com.msilb.scalanda.restapi.model.CandleFormat.BidAsk
import com.msilb.scalanda.restapi.model.Granularity.M1
import com.msilb.scalanda.restapi.model.InstrumentField
import com.msilb.scalanda.restapi.model.OrderType.{Limit, Market}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class RestConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  val testAccountId = 4393633
  val testUsername = "jachanie"
  val testPassword = "OnMuItIl"
  val restConnector = system.actorOf(RestConnector.props(accountId = testAccountId))

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll(): Unit = {
    restConnector ! ClosePositionRequest("EUR_USD")
    expectMsgType[ClosePositionResponse]
    restConnector ! GetOrdersRequest(instrument = Some("EUR_USD"))
    val orderIds = expectMsgPF() {
      case GetOrdersResponse(orders) => orders.map(_.id)
    }
    orderIds.foreach { limitOrderId =>
      restConnector ! CloseOrderRequest(limitOrderId)
      expectMsgType[CloseOrderResponse]
    }
  }

  "RestConnector" should "get full list of tradable instruments" in {
    within(5.seconds) {
      restConnector ! GetInstrumentsRequest(
        fields = Some(
          Seq(
            InstrumentField.Instrument,
            InstrumentField.DisplayName,
            InstrumentField.Pip,
            InstrumentField.MaxTradeUnits,
            InstrumentField.Precision,
            InstrumentField.MaxTrailingStop,
            InstrumentField.MinTrailingStop,
            InstrumentField.MarginRate,
            InstrumentField.Halted
          )
        )
      )
      expectMsgPF() {
        case GetInstrumentsResponse(instruments) if instruments.exists(i => i.instrument == "EUR_USD" && i.displayName.contains("EUR/USD") && i.halted.contains(false)) => true
      }
    }
  }

  it should "fetch current prices for EUR/USD and USD/JPY" in {
    within(5.seconds) {
      restConnector ! GetCurrentPricesRequest(Seq("EUR_USD", "USD_JPY"))
      expectMsgPF() {
        case GetCurrentPricesResponse(prices) if prices.size == 2 && prices.exists(_.instrument == "EUR_USD") => true
      }
    }
  }

  it should "fetch historical 1-min candles for EUR/USD" in {
    within(5.seconds) {
      restConnector ! GetCandlesRequest(instrument = "EUR_USD", count = Some(2), granularity = Some(M1), candleFormat = Some(BidAsk))
      expectMsgPF() {
        case CandleResponse("EUR_USD", M1, list) if list.size == 2 => true
      }
    }
  }

  it should "get all accounts for a test user" in {
    within(5.seconds) {
      restConnector ! GetAccountsRequest(Some(testUsername))
      expectMsgPF() {
        case GetAccountsResponse(accounts) if accounts.size == 1 && accounts.headOption.exists(_.accountId == testAccountId) => true
      }
    }
  }

  it should "create test account" in {
    within(5.seconds) {
      restConnector ! CreateTestAccountRequest()
      expectMsgPF() {
        case CreateTestAccountResponse(username, password, accountId) => true
      }
    }
  }

  it should "get account information for a test account id" in {
    within(5.seconds) {
      restConnector ! GetAccountInformationRequest(testAccountId)
      expectMsgPF() {
        case GetAccountInformationResponse(accountId, accountName, balance, unrealizedPl, realizedPl, marginUsed, marginAvail, openTrades, openOrders, marginRate, accountCurrency) if accountId == testAccountId && accountCurrency == "USD" => true
      }
    }
  }

  it should "create new limit order, retrieve order information, modify and close order" in {
    within(10.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Limit, Some(ZonedDateTime.now().plusDays(1)), Some(1.8))
      val orderId = expectMsgPF() {
        case CreateOrderResponse("EUR_USD", time, price, Some(orderOpened), tradeOpened) => orderOpened.id
      }
      restConnector ! GetOrdersRequest(instrument = Some("EUR_USD"))
      expectMsgPF() {
        case GetOrdersResponse(Seq(OrderResponse(id, "EUR_USD", 10000, Buy, Limit, time, 1.8, 0.0, 0.0, expiry, 0.0, 0.0, 0.0))) if id == orderId => true
      }
      restConnector ! GetOrderInformationRequest(orderId)
      expectMsgPF() {
        case OrderResponse(id, "EUR_USD", 10000, Buy, Limit, time, 1.8, 0.0, 0.0, expiry, 0.0, 0.0, 0.0) if id == orderId => true
      }
      restConnector ! ModifyOrderRequest(orderId, units = Some(20000))
      expectMsgPF() {
        case OrderResponse(id, "EUR_USD", 20000, Buy, Limit, time, 1.8, 0.0, 0.0, expiry, 0.0, 0.0, 0.0) if id == orderId => true
      }
      restConnector ! CloseOrderRequest(orderId)
      expectMsgPF() {
        case CloseOrderResponse(id, "EUR_USD", 20000, Buy, 1.8, time) if id == orderId => true
      }
    }
  }

  it should "create new trade, retrieve trade information, modify and close trade" in {
    within(10.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Market)
      val tradeId = expectMsgPF() {
        case CreateOrderResponse("EUR_USD", _, _, None, Some(tradeOpened)) => tradeOpened.id
      }
      restConnector ! GetOpenTradesRequest(instrument = Some("EUR_USD"))
      expectMsgPF() {
        case GetOpenTradesResponse(Seq(TradeResponse(id, 10000, Buy, "EUR_USD", _, _, 0, 0, 0, 0))) if id == tradeId => true
      }
      restConnector ! GetTradeInformationRequest(tradeId)
      expectMsgPF() {
        case TradeResponse(id, 10000, Buy, "EUR_USD", _, _, 0, 0, 0, 0) if id == tradeId => true
      }
      restConnector ! ModifyTradeRequest(tradeId, stopLoss = Some(0.8))
      expectMsgPF() {
        case TradeResponse(id, 10000, Buy, "EUR_USD", _, _, 0, 0.8, 0, 0) if id == tradeId => true
      }
      restConnector ! CloseTradeRequest(tradeId)
      expectMsgPF() {
        case CloseTradeResponse(id, _, "EUR_USD", _, Buy, _) => true
      }
    }
  }

  it should "create aggregated position, retrieve open position and close position" in {
    within(10.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Market)
      val tradeId1 = expectMsgPF() {
        case CreateOrderResponse("EUR_USD", _, _, None, Some(tradeOpened)) => tradeOpened.id
      }
      restConnector ! CreateOrderRequest("EUR_USD", 20000, Buy, Market)
      val tradeId2 = expectMsgPF() {
        case CreateOrderResponse("EUR_USD", _, _, None, Some(tradeOpened)) => tradeOpened.id
      }
      restConnector ! GetOpenPositionsRequest
      expectMsgPF() {
        case GetOpenPositionsResponse(Seq(PositionResponse("EUR_USD", 30000, Buy, _))) => true
      }
      restConnector ! GetPositionForInstrumentRequest("EUR_USD")
      expectMsgPF() {
        case PositionResponse("EUR_USD", 30000, Buy, _) => true
      }
      restConnector ! ClosePositionRequest("EUR_USD")
      expectMsgPF() {
        case ClosePositionResponse(ids, "EUR_USD", 30000, _) if ids.contains(tradeId1) && ids.contains(tradeId2) => true
      }
    }
  }

  it should "get transaction history and request detailed information on a specific transaction" in {
    within(10.seconds) {
      restConnector ! GetTransactionHistoryRequest(count = Some(20))
      val transactionId = expectMsgPF() {
        case GetTransactionHistoryResponse(transactions) => transactions.head.id
      }
      restConnector ! GetTransactionInformationRequest(transactionId)
      expectMsgPF() {
        case t: Transaction if t.id == transactionId => true
      }
    }
  }
}
