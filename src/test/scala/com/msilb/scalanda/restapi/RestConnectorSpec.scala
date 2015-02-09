package com.msilb.scalanda.restapi

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.common.model.CandleFormat.BidAsk
import com.msilb.scalanda.common.model.Granularity.M1
import com.msilb.scalanda.common.model.InstrumentField
import com.msilb.scalanda.common.model.OrderType.{Limit, Market}
import com.msilb.scalanda.common.model.Side.Buy
import com.msilb.scalanda.restapi.RestConnector.Request._
import com.msilb.scalanda.restapi.RestConnector.Response._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class RestConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val testAccountId = 4393633
  val testUsername = "jachanie"
  val testPassword = "OnMuItIl"

  val restConnector = system.actorOf(RestConnector.props(accountId = testAccountId))

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

  it should "close any existing position and order(s)" in {
    within(10.seconds) {
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
  }

  it should "create new limit order, retrieve order information, modify and delete order" in {
    within(10.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Limit, Some(ZonedDateTime.now().plusDays(1)), Some(1.8))
      val limitOrderId = expectMsgPF() {
        case CreateOrderResponse(instrument, time, price, Some(orderOpened), tradeOpened) if instrument == "EUR_USD" => orderOpened.id
      }
      restConnector ! GetOrdersRequest(instrument = Some("EUR_USD"))
      expectMsgPF() {
        case GetOrdersResponse(Seq(OrderResponse(id, "EUR_USD", 10000, Buy, Limit, time, 1.8, 0.0, 0.0, expiry, 0.0, 0.0, 0.0))) if id == limitOrderId => true
      }
      restConnector ! GetOrderInformationRequest(limitOrderId)
      expectMsgPF() {
        case OrderResponse(id, "EUR_USD", 10000, Buy, Limit, time, 1.8, 0.0, 0.0, expiry, 0.0, 0.0, 0.0) if id == limitOrderId => true
      }
      restConnector ! ModifyOrderRequest(limitOrderId, units = Some(20000))
      expectMsgPF() {
        case OrderResponse(id, "EUR_USD", 20000, Buy, Limit, time, 1.8, 0.0, 0.0, expiry, 0.0, 0.0, 0.0) if id == limitOrderId => true
      }
      restConnector ! CloseOrderRequest(limitOrderId)
      expectMsgPF() {
        case CloseOrderResponse(id, "EUR_USD", 20000, Buy, 1.8, time) if id == limitOrderId => true
      }
    }
  }

  it should "create new market order" in {
    within(5.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Market)
      expectMsgPF() {
        case CreateOrderResponse("EUR_USD", _, _, None, Some(tradeOpened)) => true
      }
    }
  }
}
