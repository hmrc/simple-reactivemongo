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

package uk.gov.hmrc.mongo.json
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNumber, JsString, Json}
import reactivemongo.bson.BSONObjectID

class ReactiveMongoFormatsSpec
  extends AnyWordSpec
     with Matchers
     with ScalaCheckPropertyChecks
     with OptionValues {

  case class Foo(id: String, name: String, _age: Int)

  val idGen: Gen[BSONObjectID] = Gen.numChar.map(_ => reactivemongo.bson.BSONObjectID.generate())

  "mongoEntity" should {
    "serialize and deserialize a BSONObjectId to JSON" in {
      import ReactiveMongoFormats._

      forAll(idGen) { id =>
        val serialized = Json.toJson(id)
        serialized shouldBe Json.obj("$oid" -> id.stringify)

        val deserialized = serialized.validate[BSONObjectID]
        deserialized.isSuccess shouldBe true
        deserialized.get       shouldBe id
      }
    }

    "replace _id with id when reading json" in {
      val json =
        Json.obj("_id" -> JsString("id"), "name" -> JsString("name"), "_age" -> JsNumber(22), "id" -> "ignored")

      val defaultFormat = Json.format[Foo]
      val entity        = ReactiveMongoFormats.mongoEntity(defaultFormat).reads(json).get
      entity shouldBe Foo("id", "name", 22)
    }

    "replace id with _id when writing json" in {
      val entity        = Foo("id", "name", 22)
      val defaultFormat = Json.format[Foo]
      val json          = ReactiveMongoFormats.mongoEntity(defaultFormat).writes(entity)
      (json \ "_id").get     shouldBe JsString("id")
      (json \ "name").get    shouldBe JsString("name")
      (json \ "_age").get    shouldBe JsNumber(22)
      (json \ "id").toOption shouldBe None
    }
  }
}
