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

package play.modules.reactivemongo

import java.util.UUID

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import reactivemongo.api.DefaultDB
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global

class InjectionSpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with ScalaFutures
     with Eventually
     with IntegrationPatience {

  private val testDbName = s"test-${this.getClass.getSimpleName}"

  override lazy val app =
    new GuiceApplicationBuilder()
      .bindings(new ReactiveMongoHmrcModule)
      .configure(Configuration("mongodb.uri" -> s"mongodb://localhost:27017/$testDbName"))
      .build()

  "Mongo" should {
    "work when ReactiveMongoComponent was injected" in {
      val component = app.injector.instanceOf[ReactiveMongoComponent]
      verify(component.mongoConnector.db)
    }
  }

  def verify(db: () => DefaultDB): Unit = {
    val collection = db().collection[JSONCollection]("test-collection")
    val doc        = Json.obj("_id" -> UUID.randomUUID().toString)

    collection.insert(doc).futureValue
    collection.find(doc).one[JsValue].futureValue shouldBe 'defined
  }

}
