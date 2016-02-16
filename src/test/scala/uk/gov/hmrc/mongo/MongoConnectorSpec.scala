package uk.gov.hmrc.mongo


import java.util.concurrent.TimeUnit

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import reactivemongo.api.{MongoConnectionOptions, MongoDriver}
import reactivemongo.core.errors.GenericDriverException

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Await, Future}
//import ExecutionContext.Implicits.global


/**
  *
  * https://docs.mongodb.org/manual/reference/connection-string/#connections-connection-options
  */
class MongoConnectorSpec extends WordSpec with Matchers  {

  "MongoConnector" should {
    "create a Mongo connection with the given options" in {

      val connector = MongoConnector("mongodb://mongo-host:2000/mongo?connectTimeoutMS=1000&socketTimeoutMS=2000", failoverStrategy = None)

      connector.db().connection.options.connectTimeoutMS shouldBe 1000
    }
  }
}


