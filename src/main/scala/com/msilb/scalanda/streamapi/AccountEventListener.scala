package com.msilb.scalanda.streamapi

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.IO
import akka.util.Timeout
import com.msilb.scalanda.common.Environment
import com.msilb.scalanda.common.Environment.SandBox
import com.msilb.scalanda.common.util.DateUtils._
import com.msilb.scalanda.streamapi.AccountEventListener.Response.AccountEvent
import spray.can.Http
import spray.can.Http.HostConnectorInfo
import spray.http._
import spray.httpx.RequestBuilding._
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.json._

import scala.concurrent.duration._

object AccountEventListener {

  def props(env: Environment = SandBox, authToken: Option[String] = None, listeners: Map[Int, Seq[ActorRef]]) = Props(new AccountEventListener(env, authToken, listeners))

  sealed trait Response

  object Response {

    case class TradeOpened(id: Int, units: Int)

    case class TradeReduced(id: Int, units: Int, pl: Double, interest: Double)

    case class Transaction(id: Int,
                           accountId: Int,
                           time: ZonedDateTime,
                           typ: String,
                           instrument: Option[String],
                           side: Option[String],
                           units: Option[Int],
                           price: Option[Double],
                           lowerBound: Option[Double],
                           upperBound: Option[Double],
                           takeProfitPrice: Option[Double],
                           stopLossPrice: Option[Double],
                           trailingStopLossDistance: Option[Double],
                           pl: Option[Double],
                           interest: Option[Double],
                           accountBalance: Option[Double],
                           tradeId: Option[Int],
                           orderId: Option[Int],
                           expiry: Option[ZonedDateTime],
                           reason: Option[String],
                           tradeOpened: Option[TradeOpened],
                           tradeReduced: Option[TradeReduced])

    case class AccountEvent(transaction: Transaction) extends Response

    object EventJsonProtocol extends DefaultJsonProtocol {
      implicit val tradeOpenedFormat = jsonFormat2(TradeOpened)
      implicit val tradeReducedFormat = jsonFormat4(TradeReduced)
      implicit val transactionFormat = jsonFormat(Transaction,
        "id",
        "accountId",
        "time",
        "type",
        "instrument",
        "side",
        "units",
        "price",
        "lowerBound",
        "upperBound",
        "takeProfitPrice",
        "stopLossPrice",
        "trailingStopLossDistance",
        "pl",
        "interest",
        "accountBalance",
        "tradeId",
        "orderId",
        "expiry",
        "reason",
        "tradeOpened",
        "tradeReduced"
      )
      implicit val accountEventFormat = jsonFormat1(AccountEvent)
    }

  }

}

class AccountEventListener(env: Environment = SandBox, authTokenOpt: Option[String] = None, listeners: Map[Int, Seq[ActorRef]]) extends Actor with ActorLogging {

  import com.msilb.scalanda.streamapi.AccountEventListener.Response.EventJsonProtocol._
  import context._

  implicit val timeout = Timeout(5.seconds)

  IO(Http) ! Http.HostConnectorSetup(
    host = env.streamApiUrl(),
    port = if (env.authenticationRequired()) 443 else 80,
    sslEncryption = env.authenticationRequired(),
    defaultHeaders = authTokenOpt.map(authToken => List(HttpHeaders.Authorization(OAuth2BearerToken(authToken)))).getOrElse(Nil)
  )

  def receive = {
    case HostConnectorInfo(hostConnector, _) =>
      hostConnector ! Get(s"/v1/events?accountIds=${listeners.keySet.mkString(",")}")
    case MessageChunk(data, _) =>
      data.asString.lines.foreach { line =>
        val entity = HttpEntity(ContentTypes.`application/json`, line)
        entity.as[AccountEvent] match {
          case Right(e) =>
            val t = e.transaction
            log.info("Received new transaction: {}", t)
            for (listenersForAccount <- listeners.get(t.accountId); listener <- listenersForAccount) {
              listener ! t
            }
          case Left(ex) => log.debug("Received heartbeat: {}", ex)
        }
      }
    case other =>
      log.debug("Received unknown message: {}", other)
  }
}
