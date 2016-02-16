package uk.gov.hmrc.mongo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import reactivemongo.core.errors.GenericDriverException

import scala.concurrent.{Await, Future}

class MongoConnectorTimeoutSpec extends WordSpec with Matchers with MongoSpecSupport with ScalaFutures with Awaiting with BeforeAndAfterEach{

  private lazy val ProxyPort = 1999

  lazy val SocketTimeout = 100
  lazy val ProxyIdleTime = 200

  override def mongoUri = s"mongodb://localhost:$ProxyPort/mongo?connectTimeoutMS=100&socketTimeoutMS=$SocketTimeout"

  "MongoConnector going via a proxy" should {

    "timeout when socket appears idle for 200 ms and the socket timeout is set to 100ms" in {

      val resultF = Future { SleepyProxy.start(ProxyPort, 27017, "localhost") }.map { ctx =>
        try {
          val repository = new SimpleTestRepository()
          await(repository.removeAll())

          ctx.setSleepTime(200)

          intercept[GenericDriverException] {
            Await.result(repository.count, timeout)
          }
        } finally {
          ctx.shutDown()
        }
      }

      await(resultF) shouldBe GenericDriverException("socket disconnected")
    }
  }
}
