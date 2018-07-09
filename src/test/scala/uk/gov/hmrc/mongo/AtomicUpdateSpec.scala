/*
 * Copyright 2018 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONNull, BSONObjectID, BSONString}

import scala.concurrent.Future

case class AtomicTestInObject(name: String, someOtherField: String, optionalValue: Option[String] = None)
case class AtomicTestObject(
  name: String,
  someOtherField: String,
  optionalValue: Option[String] = None,
  id: BSONObjectID)

case class AtomicTestObjectWithIdOverride(
  id: String,
  name: String,
  someOtherField: String,
  optionalValue: Option[String]  = None,
  idAtomic: Option[BSONObjectID] = None)

object AtomicTestObject {

  import ReactiveMongoFormats.{localDateFormats, mongoEntity, objectIdFormats}

  implicit val formats = mongoEntity {
    Json.format[AtomicTestObject]
  }
}
object AtomicTestObjectWithIdOverride {

  import ReactiveMongoFormats.{localDateFormats, mongoEntity, objectIdFormats}

  implicit val formats = mongoEntity {
    Json.format[AtomicTestObjectWithIdOverride]
  }
}

class AtomicUpdateSpec
    extends WordSpec
    with Matchers
    with MongoSpecSupport
    with BeforeAndAfterEach
    with Awaiting
    with CurrentTime {

  val repository               = new AtomicTestRepository
  val repositoryWithIdOverride = new AtomicTestRepositoryWithIdOverride

  override def beforeEach() {
    await(repository.removeAll())
    await(repositoryWithIdOverride.removeAll())
  }

  "Atomic operations" should {

    "Insert new records and verify the UpdateType returned is a 'Saved' type." in {

      val a1 = AtomicTestInObject("namea", "othervaluea")
      val a2 = AtomicTestInObject("nameb", "othervalueb")

      val result1 = await(insertRecord(a1))
      result1.updateType shouldBe a[Saved[_]]
      val result2 = await(insertRecord(a2))
      result2.updateType shouldBe a[Saved[_]]

      val result: List[AtomicTestObject] = await(repository.findAll())

      result.size           shouldBe 2
      result.head.name      shouldBe (a1.name)
      result.head.id        shouldBe a[BSONObjectID]
      result.tail.head.name shouldBe (a2.name)
      result.tail.head.id   shouldBe a[BSONObjectID]
    }

    "Fail with a JsResultException if the Reads object fails to decode the stored JSON." in {

      val incomplete = "some name"

      val result =
        repository.atomicUpsert(findByField(incomplete), BSONDocument("$set" -> BSONDocument("name" -> incomplete)))

      await(result.failed) shouldBe a[JsResultException]
    }

    "Create new records overriding the object Id attribute, verify atomics can return the correct update type of 'Saved'." in {

      val a1 = AtomicTestObjectWithIdOverride("id1", "namea", "othervaluea")
      val a2 = AtomicTestObjectWithIdOverride("id2", "nameb", "othervalueb")

      val result1 = await(insertRecordWithIdOverride(a1, "idAtomic"))
      result1.updateType shouldBe a[Saved[_]]

      val result2 = await(insertRecordWithIdOverride(a2, "idAtomic"))
      result2.updateType shouldBe a[Saved[_]]

      val result: List[AtomicTestObjectWithIdOverride] = await(repositoryWithIdOverride.findAll())

      result.size      shouldBe 2
      result.head.name shouldBe (a1.name)

      result.head.idAtomic.get      shouldBe a[BSONObjectID]
      result.tail.head.name         shouldBe (a2.name)
      result.tail.head.idAtomic.get shouldBe a[BSONObjectID]
    }

    "Find an exiting record and only update a single record attribute, the type returned from the update operation will be 'Updated'. " in {

      val existingRecord = AtomicTestObject("namea", "othervalueb", id = BSONObjectID.generate)
      val updateRecord   = existingRecord.copy(name                    = "updated")

      await(repository.save(existingRecord))

      val atomicResult = await(
        repository.atomicUpsert(
          findByField(existingRecord.name),
          BSONDocument(
            "$set" -> BSONDocument("name" -> updateRecord.name)
          )))
      atomicResult.updateType shouldBe a[Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll())
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Find an existing record and update multiple record fields atomicUpsert" in {

      val existingRecord = AtomicTestObject("namea", "othervalueb", id = BSONObjectID.generate)
      val updateRecord   = existingRecord.copy(name                    = "updated", someOtherField = "other data")

      await(repository.save(existingRecord))

      val atomicResult = await(
        repository.atomicUpsert(
          findByField(existingRecord.name),
          BSONDocument(
            "$set" -> BSONDocument("name"           -> updateRecord.name),
            "$set" -> BSONDocument("someOtherField" -> updateRecord.someOtherField)
          )
        ))
      atomicResult.updateType shouldBe an[Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll())
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Find an existing record and update multiple record fields using atomicUpdate" in {

      val existingRecord = AtomicTestObject("namea", "othervalueb", id = BSONObjectID.generate)
      val updateRecord   = existingRecord.copy(name                    = "updated", someOtherField = "other data")

      await(repository.save(existingRecord))

      val atomicResult = await(
        repository.atomicUpdate(
          findByField(existingRecord.name),
          BSONDocument(
            "$set" -> BSONDocument("name"           -> updateRecord.name),
            "$set" -> BSONDocument("someOtherField" -> updateRecord.someOtherField)
          )
        ))
      atomicResult                shouldBe defined
      atomicResult.get.updateType shouldBe an[Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll())
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Find an exiting record and reset an optional record field " in {

      val existingRecord = AtomicTestObject("namea", "othervalueb", Some("optional data"), id = BSONObjectID.generate)
      val updateRecord   = existingRecord.copy(optionalValue                                  = None)

      await(repository.save(existingRecord))

      val atomicResult = await(
        repository.atomicUpsert(
          findByField(existingRecord.name),
          BSONDocument(
            "$unset" -> BSONDocument("optionalValue" -> BSONNull)
          )))
      atomicResult.updateType shouldBe a[Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll())
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Attempting to update a record which does not exist using atomicSaveOrUpdate will result in no record returned" in {

      val result = await(
        repository.atomicSaveOrUpdate(
          findByField("non-existing"),
          BSONDocument(
            "$set" -> BSONDocument("name" -> "newname")
          ),
          false))
      result shouldBe None
    }

    "Attempting to update a record which does not exist using atomicUpdate will result in no record returned" in {

      val result = await(
        repository.atomicUpdate(
          findByField("non-existing"),
          BSONDocument(
            "$set" -> BSONDocument("name" -> "newname")
          )))
      result shouldBe None
    }
  }

  def findByField(field: String) =
    BSONDocument("name" -> BSONString(field))

  def insertRecord(testObj: AtomicTestInObject): Future[DatabaseUpdate[_]] =
    repository.atomicUpsert(
      findByField(testObj.name),
      BSONDocument(
        "$set" -> BSONDocument("name"           -> testObj.name),
        "$set" -> BSONDocument("someOtherField" -> testObj.someOtherField)
      ))

  def insertRecordWithIdOverride(
    testObj: AtomicTestObjectWithIdOverride,
    idAttribute: String): Future[DatabaseUpdate[_]] =
    repositoryWithIdOverride.atomicUpsert(
      findByField(testObj.name),
      BSONDocument(
        "$setOnInsert" -> BSONDocument("_id"            -> testObj.id),
        "$set"         -> BSONDocument("name"           -> testObj.name),
        "$set"         -> BSONDocument("someOtherField" -> testObj.someOtherField)
      ),
      idAttribute
    )

  class AtomicTestRepository(implicit mc: MongoConnector)
      extends ReactiveRepository[AtomicTestObject, BSONObjectID](
        "atomicTestRepository",
        mc.db,
        AtomicTestObject.formats,
        ReactiveMongoFormats.objectIdFormats)
      with AtomicUpdate[AtomicTestObject] {

    override def indexes = Seq(
      Index(Seq("name" -> IndexType.Ascending), name = Some("aNameUniqueIdx"), unique = true, sparse = true)
    )

    override def isInsertion(suppliedId: BSONObjectID, returned: AtomicTestObject): Boolean =
      suppliedId.equals(returned.id)
  }

  class AtomicTestRepositoryWithIdOverride(implicit mc: MongoConnector)
      extends ReactiveRepository[AtomicTestObjectWithIdOverride, BSONObjectID](
        "atomicIdOverrideTestRepository",
        mc.db,
        AtomicTestObjectWithIdOverride.formats,
        ReactiveMongoFormats.objectIdFormats)
      with AtomicUpdate[AtomicTestObjectWithIdOverride] {

    override def indexes = Seq(
      Index(Seq("name" -> IndexType.Ascending), name = Some("aNameUniqueIdx"), unique = true, sparse = true)
    )

    override def isInsertion(suppliedId: BSONObjectID, returned: AtomicTestObjectWithIdOverride): Boolean =
      returned.idAtomic match {
        case Some(id) => suppliedId.equals(id)
        case _        => false
      }
  }

}
