/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}

/**
  *
  * https://docs.mongodb.org/manual/reference/connection-string/#connections-connection-options
  */
class MongoConnectorSpec extends WordSpec with Matchers {

  "MongoConnector" should {
    "create a Mongo connection with the given options" in {

      val connector = MongoConnector(
        "mongodb://127.0.0.1:27017/test?connectTimeoutMS=1000&socketTimeoutMS=2000",
        failoverStrategy = None)

      connector.db().connection.options.connectTimeoutMS shouldBe 1000
    }
  }
}
