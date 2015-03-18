/*
 * Copyright 2015 HM Revenue & Customs
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
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.bson.{BSONObjectID, BSONDocument}
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.{BSONNull, BSONString, BSONDocument, BSONObjectID}

import scala.concurrent.Future

case class AtomicTestInObject(name:String, someOtherField:String, optionalValue:Option[String]=None)
case class AtomicTestObject(name:String, someOtherField:String, optionalValue:Option[String]=None, id: BSONObjectID)

object AtomicTestObject {

  import ReactiveMongoFormats.{objectIdFormats, localDateFormats, mongoEntity}

  implicit val formats = mongoEntity {
    Json.format[AtomicTestObject]
  }
}

class AtomicUpdateSpec extends WordSpec with Matchers with MongoSpecSupport with BeforeAndAfterEach with Awaiting with CurrentTime {

  val repository = new AtomicTestRepository

  override def beforeEach() {
    await(repository.removeAll)
  }

  "Atomic operations" should {

    "Create new records" in {

      val a1 = AtomicTestInObject("namea", "othervaluea")
      val a2 = AtomicTestInObject("nameb", "othervalueb")

      val result1=await(insertRecord(a1))
      result1.updateType shouldBe a [Saved[_]]
      val result2=await(insertRecord(a2))
      result2.updateType shouldBe a [Saved[_]]

      val result: List[AtomicTestObject] = await(repository.findAll)

      result.size shouldBe 2
      result.head.name shouldBe(a1.name)
      result.head.id shouldBe a[BSONObjectID]
      result.tail.head.name shouldBe(a2.name)
      result.tail.head.id shouldBe a[BSONObjectID]
    }

    "Find an exiting record and only update a single record attribute " in {

      val existingRecord=AtomicTestObject("namea","othervalueb", id=BSONObjectID.generate)
      val updateRecord=existingRecord.copy(name="updated")

      await(repository.save(existingRecord))

      val atomicResult=await( repository.atomicUpsert(findByField(existingRecord.name),
        BSONDocument(
          "$set" -> BSONDocument("name" -> updateRecord.name)
        ))
      )
      atomicResult.updateType shouldBe a [Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll)
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Find an exiting record and update multiple record fields " in {

      val existingRecord=AtomicTestObject("namea","othervalueb", id = BSONObjectID.generate)
      val updateRecord=existingRecord.copy(name="updated", someOtherField="other data")

      await(repository.save(existingRecord))

      val atomicResult=await( repository.atomicUpsert(findByField(existingRecord.name),
        BSONDocument(
          "$set" -> BSONDocument("name" -> updateRecord.name),
          "$set" -> BSONDocument("someOtherField" -> updateRecord.someOtherField)
        ))
      )
      atomicResult.updateType shouldBe a [Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll)
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Find an exiting record and reset an optional record field " in {

      val existingRecord=AtomicTestObject("namea","othervalueb", Some("optional data"), id = BSONObjectID.generate)
      val updateRecord=existingRecord.copy(optionalValue=None)

      await(repository.save(existingRecord))

      val atomicResult=await( repository.atomicUpsert(findByField(existingRecord.name),
        BSONDocument(
          "$unset" -> BSONDocument("optionalValue" -> BSONNull)
        ))
      )
      atomicResult.updateType shouldBe a [Updated[_]]

      val result: List[AtomicTestObject] = await(repository.findAll)
      result.size shouldBe 1
      result.head shouldBe updateRecord
    }

    "Attempting to update a record which does not exist will result in no record returned" in {

      val existingRecord=AtomicTestObject("ddd", "eee", id=BSONObjectID.generate)

      val result=await(repository.atomicSaveOrUpdate(findByField(existingRecord.name),
            BSONDocument(
            "$set" -> BSONDocument("name" -> "newname")
        ),false))
      result shouldBe None
    }
  }

  def findByField(field: String) = {
    BSONDocument("name" -> BSONString(field))
  }

  def insertRecord(testObj:AtomicTestInObject) : Future[DatabaseUpdate[_]] = {
    repository.atomicUpsert(findByField(testObj.name),
      BSONDocument(
        "$set" -> BSONDocument("name" -> testObj.name),
        "$set" -> BSONDocument("someOtherField" -> testObj.someOtherField)
      ))
  }

  class AtomicTestRepository(implicit mc: MongoConnector)
    extends ReactiveRepository[AtomicTestObject, BSONObjectID]("simpleTestRepository", mc.db, AtomicTestObject.formats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[AtomicTestObject] {

    override def indexes = Seq(
      Index(Seq("name" -> IndexType.Ascending), name = Some("aNameUniqueIdx"), unique = true, sparse = true)
    )

    override def isInsertion(suppliedId: BSONObjectID, returned: AtomicTestObject): Boolean =
      suppliedId.equals(returned.id)
  }

}
