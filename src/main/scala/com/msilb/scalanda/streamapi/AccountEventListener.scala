package com.msilb.scalanda.streamapi

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.IO
import akka.util.Timeout
import com.msilb.scalanda.common.Environment
import com.msilb.scalanda.common.Environment.SandBox
import com.msilb.scalanda.common.model.Transaction
import com.msilb.scalanda.common.model.Transaction.TransactionJsonProtocol._
import com.msilb.scalanda.common.util.DateUtils.DateJsonFormat
import com.msilb.scalanda.streamapi.AccountEventListener.Heartbeat
import spray.can.Http
import spray.can.Http.HostConnectorInfo
import spray.http._
import spray.httpx.RequestBuilding._
import spray.json._

import scala.concurrent.duration._

object AccountEventListener {

  def props(env: Environment = SandBox, authToken: Option[String] = None, listeners: Map[Int, Seq[ActorRef]]) = Props(new AccountEventListener(env, authToken, listeners))

  case class Heartbeat(time: ZonedDateTime)

  implicit val heartbeatFmt = jsonFormat1(Heartbeat)

}

class AccountEventListener(env: Environment = SandBox, authTokenOpt: Option[String] = None, listeners: Map[Int, Seq[ActorRef]]) extends Actor with ActorLogging {

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
        line.parseJson.asJsObject.fields.head match {
          case ("transaction", obj) =>
            val t = obj.convertTo[Transaction]
            log.info("Received new transaction: {}", t)
            for (listenersForAccount <- listeners.get(t.accountId); listener <- listenersForAccount) {
              listener ! t
            }
          case ("heartbeat", obj) =>
            val h = obj.convertTo[Heartbeat]
            log.debug("Received heartbeat: {}", h)
          case (unknown, _) =>
            log.warning("Unknown event received: {}", unknown)
        }
      }
    case other =>
      log.debug("Received unknown message: {}", other)
  }
}
