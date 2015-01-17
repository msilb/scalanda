import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.msilb.scalanda.restapi.Connector
import com.msilb.scalanda.restapi.Connector.Request.GetCandlesRequest
import com.msilb.scalanda.restapi.Connector.Response.CandleResponse
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("test"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "Connector" must {

    "be able to fetch historical candles" in {
      val con = system.actorOf(Connector.props())
      within(2.seconds) {
        con ! GetCandlesRequest("EUR_USD", 2, "M1", "bidask")
        expectMsgPF() {
          case CandleResponse("EUR_USD", "M1", list) if list.size == 2 => true
        }
      }
    }
  }
}
