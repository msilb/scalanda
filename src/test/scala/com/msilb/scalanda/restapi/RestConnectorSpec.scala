package com.msilb.scalanda.restapi

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.restapi.RestConnector.Request.{ClosePositionRequest, CreateOrderRequest, GetCandlesRequest}
import com.msilb.scalanda.restapi.RestConnector.Response.{CandleResponse, ClosePositionResponse, CreateOrderResponse}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class RestConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val restConnector = system.actorOf(RestConnector.props(accountId = 8164566))

  "RestConnector" should "fetch 1 min candles in EUR/USD" in {
    within(2.seconds) {
      restConnector ! GetCandlesRequest("EUR_USD", 2, "M1", "bidask")
      expectMsgPF() {
        case CandleResponse("EUR_USD", "M1", list) if list.size == 2 => true
      }
    }
  }

  it should "create new market order in EUR/USD" in {
    within(2.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, "buy", "market")
      expectMsgPF() {
        case CreateOrderResponse("EUR_USD", _, _, None, Some(tradeOpened)) => true
      }
    }
  }

  it should "close existing position in EUR/USD" in {
    within(2.seconds) {
      restConnector ! ClosePositionRequest("EUR_USD")
      expectMsgPF() {
        case ClosePositionResponse(_, "EUR_USD", 10000, _) => true
      }
    }
  }
}
