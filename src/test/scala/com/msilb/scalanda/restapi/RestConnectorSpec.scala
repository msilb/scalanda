package com.msilb.scalanda.restapi

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.restapi.RestConnector.Request._
import com.msilb.scalanda.restapi.RestConnector.Response._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class RestConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val restConnector = system.actorOf(RestConnector.props(accountId = 8164566))

  "RestConnector" should "get full list of tradable instruments containing EUR/USD" in {
    within(5.seconds) {
      restConnector ! GetInstrumentsRequest(
        fields = Some(
          Seq(
            "instrument",
            "displayName",
            "pip",
            "maxTradeUnits",
            "precision",
            "maxTrailingStop",
            "minTrailingStop",
            "marginRate",
            "halted"
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
      restConnector ! GetCandlesRequest("EUR_USD", 2, "M1", "bidask")
      expectMsgPF() {
        case CandleResponse("EUR_USD", "M1", list) if list.size == 2 => true
      }
    }
  }

  it should "create new market order in EUR/USD" in {
    within(5.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, "buy", "market")
      expectMsgPF() {
        case CreateOrderResponse("EUR_USD", _, _, None, Some(tradeOpened)) => true
      }
    }
  }

  it should "close existing position in EUR/USD" in {
    within(5.seconds) {
      restConnector ! ClosePositionRequest("EUR_USD")
      expectMsgPF() {
        case ClosePositionResponse(_, "EUR_USD", _, _) => true
      }
    }
  }
}
