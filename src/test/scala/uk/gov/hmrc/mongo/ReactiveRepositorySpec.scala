/*
 * Copyright 2014 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.hmrc.mongo

import reactivemongo.bson.BSONObjectID
import org.scalatest.{BeforeAndAfterEach, WordSpec, Matchers}
import scala.concurrent.Future
import play.api.libs.json.Json
import reactivemongo.core.errors.DatabaseException

case class NestedModel(a : String, b : String)

case class TestObject(aField: String,
                      anotherField: Option[String] = None,
                      optionalCollection : Option[List[NestedModel]] = None,
                      nestedMapOfCollections : Map[String, List[Map[String, Seq[NestedModel]]]] = Map.empty,
                      crud: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
                      id: BSONObjectID = BSONObjectID.generate)

object TestObject {
  import ReactiveMongoFormats.{objectIdFormats, mongoEntity}

  implicit val nestedModelformats = Json.format[NestedModel]

  implicit val formats = mongoEntity{
    Json.format[TestObject]
  }
}

class SimpleTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[TestObject, BSONObjectID]("simpleTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {

  import reactivemongo.api.indexes.IndexType
  import reactivemongo.api.indexes.Index

  override def ensureIndexes() {
    collection.indexesManager.ensure(Index(Seq("aField" -> IndexType.Ascending), name = Some("aFieldUniqueIdx"), unique = true, sparse = true))
  }
}

class ReactiveRepositorySpec extends WordSpec with Matchers with MongoSpecSupport with BeforeAndAfterEach with Awaiting {

  val repository = new SimpleTestRepository

  override def beforeEach() {
    await(repository.removeAll)
  }

  "findAll" should {

    "return all created records" in {

      val e1 = TestObject("1")
      val e2 = TestObject("2", optionalCollection = Some(List(NestedModel("A", "B"), NestedModel("C", "D"))))
      val e3 = TestObject("3", nestedMapOfCollections = Map(
        "level_one" -> List(
          Map("level_two_1" -> Seq(NestedModel("A1", "B1"), NestedModel("C1", "D1"), NestedModel("E1", "F1"))),
          Map("level_two_2" -> Seq(NestedModel("A2", "B2"))),
          Map("level_two_3" -> Seq(NestedModel("A1", "B1"), NestedModel("C1", "D1"), NestedModel("E1", "F1"), NestedModel("G2", "H2")))
        )
      ))
      val e4 = TestObject("4")

      val created = for {
        res1 <- repository.save(e1)
        res2 <- repository.save(e2)
        res3 <- repository.save(e3)
        countResult <- repository.count
      } yield countResult

      await(created) shouldBe 3

      val result: List[TestObject] = await(repository.findAll)
      result.size shouldBe 3
      result should contain(e1)
      result should contain(e2)
      result should contain(e3)

      result should not contain (e4)

    }

  }

  "findById" should {

    "return an existing record" in {
      val e1 = TestObject("1")

      await(repository.save(e1))

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should not be None
      result should be(Some(e1))
    }

    "return none for nonexistent ID" in {
      val result: Option[TestObject] = await(repository.findById(BSONObjectID.generate))
      result should be(None)
    }

  }

  "saveOrUpdate" should {

    "update an existing record" in {
      val e1 = TestObject("1")

      await(repository.save(e1))


      val result = await(repository.saveOrUpdate(repository.findById(e1.id), Future.successful(TestObject("2")), _.copy(aField = "3")))

      result.updateType match {
        case Updated(_, _) => // ok
        case Saved(_) => fail
      }

      val updatedRecord = await(repository.findById(e1.id))
      updatedRecord.get.aField shouldBe "3"
    }

    "return a default value, with modifiers applied, if the record is not found" in {
      val e1 = TestObject("new")

      val modifiers: (TestObject) => TestObject = _.copy(aField = "3")

      val result = await(repository.saveOrUpdate(repository.findById(BSONObjectID.generate), Future.successful(e1), modifiers))

      result.updateType match {
        case Updated(_, _) => fail
        case Saved(s) => s.aField shouldBe "3"
      }

      val updatedRecord = await(repository.findById(e1.id))
      updatedRecord.get.aField shouldBe "3"
    }
  }

  "Indexes" should {
    "be created via ensureIndexes method" in {

      val uniqueField = "i_am_a_unique_field"
      val saveWithoutError = TestObject(uniqueField)

      await(repository.save(saveWithoutError))

      val shouldNotSave = TestObject(uniqueField)

      intercept[DatabaseException] {
        await(repository.save(shouldNotSave))
      }

    }
  }

}