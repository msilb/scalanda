package com.msilb.scalanda.streamapi

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.restapi.model.OrderType
import OrderType.Market
import com.msilb.scalanda.common.model.Side.Buy
import com.msilb.scalanda.common.model.Transaction
import com.msilb.scalanda.restapi.Request.{ClosePositionRequest, CreateOrderRequest}
import com.msilb.scalanda.restapi.Response.{ClosePositionResponse, CreateOrderResponse}
import com.msilb.scalanda.restapi.RestConnector
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class AccountEventListenerSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val testAccountId = 8164566

  val restConnector = system.actorOf(RestConnector.props(accountId = testAccountId))
  val accountEventListener = system.actorOf(AccountEventListener.props(listeners = Map(testAccountId -> Seq(testActor))))

  "AccountEventListener" should "receive event when new market order is placed" in {
    within(10.seconds) {
      restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Market)
      restConnector ! ClosePositionRequest("EUR_USD")
      expectMsgAnyClassOf(classOf[CreateOrderResponse], classOf[ClosePositionResponse], classOf[Transaction])
    }
  }
}
