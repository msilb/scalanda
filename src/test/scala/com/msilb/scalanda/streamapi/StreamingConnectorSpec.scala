package com.msilb.scalanda.streamapi

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.common.model.Side.Buy
import com.msilb.scalanda.common.model.Transaction.MarketOrderCreate
import com.msilb.scalanda.restapi.Request.{ClosePositionRequest, CreateOrderRequest}
import com.msilb.scalanda.restapi.RestConnector
import com.msilb.scalanda.restapi.model.OrderType.Market
import com.msilb.scalanda.streamapi.StreamingConnector._
import com.msilb.scalanda.streamapi.model.Tick
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class StreamingConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val testAccountId = 6535195

  val restConnector = system.actorOf(RestConnector.props(accountId = testAccountId))
  val streamingConnector = system.actorOf(StreamingConnector.props)

  "StreamingConnector" should "successfully connect to the streaming end-point" in {
    within(5.seconds) {
      streamingConnector ! Connect()
      expectMsg(ConnectionEstablished)
    }
  }

  it should "register listeners" in {
    within(5.seconds) {
      streamingConnector ! AddListeners(Set(testActor))
      expectMsg(Set(testActor))
    }
  }

  it should "subscribe for price stream and receive price ticks" in {
    within(5.seconds) {
      streamingConnector ! StartRatesStreaming(testAccountId, Set("EUR_USD"))
      expectMsgType[Tick]
    }
  }

  it should "subscribe for events stream and receive account events" in {
    within(5.seconds) {
      streamingConnector ! StartEventsStreaming(Some(Set(testAccountId)))
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Market)
      restConnector ! ClosePositionRequest("EUR_USD")
      fishForMessage() {
        case t: MarketOrderCreate if t.instrument == "EUR_USD" && t.side == Buy && t.units == 10000 => true
        case _ => false
      }
    }
  }

  it should "de-register listeners" in {
    within(5.seconds) {
      streamingConnector ! RemoveListeners(Set(testActor))
      fishForMessage() {
        case s: Set[_] if s.isEmpty => true
        case _ => false
      }
    }
  }
}
