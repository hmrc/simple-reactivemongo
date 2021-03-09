/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mongo

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactivemongo.api.MongoConnectionOptions

/**
  *
  * https://docs.mongodb.org/manual/reference/connection-string/#connections-connection-options
  */
class MongoConnectorSpec extends AnyWordSpec with Matchers {

  private val defaultHeartbeatFrequencyMS: Int = MongoConnectionOptions.default.heartbeatFrequencyMS

  "MongoConnector" should {
    "create a Mongo connection with the given options" in {
      val connector = MongoConnector(
        "mongodb://127.0.0.1:27017/test?connectTimeoutMS=1000&socketTimeoutMS=2000",
        failoverStrategy = None)

      connector.db().connection.options.connectTimeoutMS shouldBe 1000
    }

    "set heartbeatFrequencyMS with provided default" in {
      val connector = MongoConnector(
        s"mongodb://127.0.0.1:27017/test",
        defaultHeartbeatFrequencyMS = Some(4000)
      )

      connector.db().connection.options.heartbeatFrequencyMS shouldBe 4000
    }

    "use connection string heartbeatFrequencyMS if it exists" in {
      val connector = MongoConnector(
        s"mongodb://127.0.0.1:27017/test?heartbeatFrequencyMS=4000",
        defaultHeartbeatFrequencyMS = Some(5000)
      )

      connector.db().connection.options.heartbeatFrequencyMS shouldBe 4000
    }

    "use the deprecated 'rm.monitorRefreshMS' in connection string if it exists" in {
      val connector = MongoConnector(
        s"mongodb://127.0.0.1:27017/test?rm.monitorRefreshMS=4000",
        defaultHeartbeatFrequencyMS = Some(5000)
      )

      connector.db().connection.options.heartbeatFrequencyMS shouldBe 4000
    }

    "fallback to default if neither option set" in {
      val value = defaultHeartbeatFrequencyMS
      val connector = MongoConnector(
        s"mongodb://127.0.0.1:27017/test",
        defaultHeartbeatFrequencyMS = None
      )

      connector.db().connection.options.heartbeatFrequencyMS shouldBe value
    }
  }
}
