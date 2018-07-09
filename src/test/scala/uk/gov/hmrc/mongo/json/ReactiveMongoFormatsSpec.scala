package uk.gov.hmrc.mongo.json

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsString, JsUndefined, Json}

class ReactiveMongoFormatsSpec extends WordSpec with Matchers {

  case class Foo(id: String, name: String, _age: Int)

  "mongoEntity" should {

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
