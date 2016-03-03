package uk.gov.hmrc.mongo

import org.scalatest.{Matchers, WordSpec}


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


