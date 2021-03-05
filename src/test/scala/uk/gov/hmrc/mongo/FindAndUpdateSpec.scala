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

import com.outworkers.util.samplers._
import org.scalatest.{BeforeAndAfterEach, OptionValues, LoneElement}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{Json, OFormat}
import reactivemongo.api.DefaultDB

class FindAndUpdateSpec
  extends AnyWordSpec
    with Matchers
    with MongoSpecSupport
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach
    with OptionValues
    with Awaiting
    with LoneElement {

  val repo = new ExampleRepo(mongo)

  override def beforeEach() {
    await(repo.removeAll())
  }

  "FindAndUpdate" should {
    "update a specified document" in {
      val example            = gen[Example]
      val others             = List.fill(5)(gen[Example])
      val updatedDescription = gen[String]

      repo.bulkInsert(example +: others).futureValue
      repo.findAll().futureValue should contain(example)

      val result = repo
        .findAndUpdate(
          query  = Json.obj("key"  -> example.key),
          update = Json.obj("$set" -> Json.obj("descr" -> updatedDescription))
        )
        .futureValue

      result.value.value.as[Example] shouldBe example // original document
      repo.find("key" -> example.key).futureValue.loneElement shouldBe Example(example.key, updatedDescription)
    }

    "return modified document if 'fetchNewObject' is set to true" in {
      val example            = gen[Example]
      val updatedDescription = gen[String]

      val chain = for {
        _ <- repo.insert(example)
        result <- repo.findAndUpdate(
                   query          = Json.obj("key" -> example.key),
                   update         = Json.obj("$set" -> Json.obj("descr" -> updatedDescription)),
                   fetchNewObject = true)
      } yield result

      chain.futureValue.value.value.as[Example] shouldBe example.copy(descr = updatedDescription)
    }

    "add a document if upsert is set to true and no document match the query" in {
      repo.findAll().futureValue shouldBe Nil

      val descr = gen[String]
      val key   = gen[String]

      repo
        .findAndUpdate(
          query  = Json.obj("key" -> "doesn-exist"),
          update = Json.obj("$set" -> Json.obj("key" -> key, "descr" -> descr)),
          upsert = true
        )
        .futureValue

      repo.findAll().futureValue.loneElement shouldBe Example(key, descr)
    }

    "modify first document as narrowed down by 'sort' field if query matches multiple documents" in {
      val example              = Example("key", descr = "Z")
      val fullDuplicates       = List.fill(2)(example)
      val distinguishingDescr  = "A"
      val docWithDiffDescr     = example.copy(descr = distinguishingDescr)
      val modifiedDescr        = "B"
      val ascSortByDescription = Json.obj("descr" -> 1)

      repo.bulkInsert(docWithDiffDescr :: fullDuplicates).futureValue

      repo
        .findAndUpdate(
          query  = Json.obj("key" -> example.key),
          update = Json.obj("$set" -> Json.obj("key" -> example.key, "descr" -> modifiedDescr)),
          upsert = true,
          sort   = Some(ascSortByDescription)
        )
        .futureValue

      val expectedResult = docWithDiffDescr.copy(descr = modifiedDescr) :: fullDuplicates
      repo.findAll().futureValue should contain theSameElementsAs expectedResult

    }

    "project returned documents based on 'fields' value" in {
      val example       = gen[Example]
      val modifiedDescr = gen[String]
      repo.insert(example).futureValue

      val result = repo
        .findAndUpdate(
          query          = Json.obj("key" -> example.key),
          update         = Json.obj("$set" -> Json.obj("key" -> example.key, "descr" -> modifiedDescr)),
          fetchNewObject = true,
          fields         = Some(Json.obj("descr" -> 1))
        )
        .futureValue

      val descrField = (result.value.value \ "descr").validate[String]
      descrField.isSuccess shouldBe true
      descrField.asOpt.value shouldEqual modifiedDescr

      (result.value.value \ "key").toOption.isEmpty shouldBe true
    }
  }
}

final case class Example(key: String, descr: String)

object Example {
  implicit val format: OFormat[Example] = Json.format[Example]
}

class ExampleRepo(mongo: () => DefaultDB) extends ReactiveRepository("find-and-modify", mongo, Example.format)
