/*
 * Copyright 2019 HM Revenue & Customs
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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import com.outworkers.util.samplers._
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.slf4j.LoggerFactory
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._
import uk.gov.hmrc.mongo.json.{ReactiveMongoFormats, TupleFormats}

import scala.concurrent.ExecutionContext

class ReactiveRepositorySpec
    extends WordSpec
    with Matchers
    with MongoSpecSupport
    with BeforeAndAfterEach
    with Awaiting
    with Eventually
    with LogCapturing {

  val repository            = new SimpleTestRepository
  val uniqueIndexRepository = new FailingIndexesTestRepository

  override def beforeEach() {
    await(repository.removeAll())
  }

  "findAll" should {

    "return all created records" in {

      val e1 = TestObject("1")
      val e2 = TestObject("2", optionalCollection = Some(List(NestedModel("A", "B"), NestedModel("C", "D"))))
      val e3 = TestObject(
        "3",
        nestedMapOfCollections = Map(
          "level_one" -> List(
            Map("level_two_1" -> Seq(NestedModel("A1", "B1"), NestedModel("C1", "D1"), NestedModel("E1", "F1"))),
            Map("level_two_2" -> Seq(NestedModel("A2", "B2"))),
            Map(
              "level_two_3" -> Seq(
                NestedModel("A1", "B1"),
                NestedModel("C1", "D1"),
                NestedModel("E1", "F1"),
                NestedModel("G2", "H2")))
          )
        )
      )
      val e4 = TestObject("4")

      val created = for {
        _           <- repository.insert(e1)
        _           <- repository.insert(e2)
        _           <- repository.insert(e3)
        countResult <- repository.count
      } yield countResult

      await(created) shouldBe 3

      val result: List[TestObject] = await(repository.findAll())
      result.size shouldBe 3
      result      should contain(e1)
      result      should contain(e2)
      result      should contain(e3)

      result should not contain e4
    }
  }

  "findById" should {

    "return an existing record" in {
      val e1 = TestObject("1")

      await(repository.insert(e1))

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should not be None
      result should be(Some(e1))
    }

    "return none for nonexistent ID" in {
      val result: Option[TestObject] = await(repository.findById(BSONObjectID.generate))
      result should be(None)
    }

  }

  "removeById" should {

    "remove the identified record" in {
      val e1 = TestObject("1")

      await(repository.insert(e1))

      val removeResult = await(repository.removeById(e1.id))
      val inError      = !removeResult.ok || removeResult.code.isDefined

      inError shouldBe false

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should be(None)
    }
  }

  "remove with provided query" should {

    "remove by one field" in {
      val e1 = TestObject("1", Some("used to identify"))

      await(repository.insert(e1))

      val removeResult = await(repository.remove("anotherField" -> "used to identify"))
      val inError      = !removeResult.ok || removeResult.code.isDefined
      inError shouldBe false

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should be(None)
    }

    "remove by multiple field query" in {

      import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.objectIdFormats

      val e1 = TestObject("1", Some("used to identify"))

      await(repository.insert(e1))

      val removeResult = await(repository.remove("_id" -> e1.id, "anotherField" -> "used to identify"))
      val inError      = !removeResult.ok || removeResult.code.isDefined
      inError shouldBe false

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should be(None)
    }
  }

  "Saving an object" should {
    "not upsert" in {

      await(repository.drop)
      await(repository.ensureIndexes)

      val originalSave = TestObject("orignal-save")
      await(repository.insert(originalSave))

      val notUpsert = originalSave.copy("should-not-upsert")
      a[DatabaseException] should be thrownBy await(repository.insert(notUpsert))
    }
  }

  "Creation of Indexes" should {
    "be done based on provided Indexes" in new LogCapturing {
      await(repository.drop)
      await(repository.ensureIndexes)
      await(repository.insert(TestObject("random_object")))

      val indexes = await(repository.collection.indexesManager.list()).map(f => f.name.getOrElse(""))
      indexes should contain("aFieldUniqueIdx")
      indexes should contain("anotherFieldIndex")
    }

    "mean that exceptions are thrown when saving a duplicate record on a unique index" in {
      val uniqueField      = "i_am_a_unique_field"
      val saveWithoutError = TestObject(uniqueField)
      val shouldNotSave    = TestObject(uniqueField)

      await(repository.drop)
      await(repository.ensureIndexes)

      await(repository.insert(saveWithoutError))

      a[DatabaseException] should be thrownBy await(repository.insert(shouldNotSave))
    }

    "not log errors when all are created successfully" in {
      withCaptureOfLoggingFrom[SimpleTestRepository] { logList =>
        await(repository.drop)
        await(repository.ensureIndexes)
        await(repository.insert(TestObject("random_object")))

        eventually(timeout(Span(5, Seconds))) {
          logList shouldBe empty
        }
      }
    }

    "log any error when index fails to create" in {
      withCaptureOfLoggingFrom[FailingIndexesTestRepository] { logList =>
        await(uniqueIndexRepository.drop)

        await(uniqueIndexRepository.insert(TestObject("uniqueKey", Some("bogus"))))
        await(uniqueIndexRepository.insert(TestObject("uniqueKey", Some("whatever"))))

        await(uniqueIndexRepository.ensureIndexes) shouldBe Seq(false)
        logList.size                               should be(1)
        logList.head.getMessage contains s"${uniqueIndexRepository.message} (${uniqueIndexRepository.indexName})"
      }
    }

    "ignore already applied index" in {
      withCaptureOfLoggingFrom[FailingIndexesTestRepository] { logList =>
        await(repository.ensureIndexes) shouldBe Seq(false, false)
        logList.size                    should be(0)
      }

      await(repository.collection.indexesManager.list()).size shouldBe 3
    }
  }

  "Storing a raw JsValue" should {
    "be able to be queried" in {

      val unknownStructure = Json.toJson(
        Map(
          "key1" -> Json.toJson("top level value"),
          "key2" -> Json.toJson(List(1, 2, 3)),
          "key3" -> Json.toJson(42.0),
          "key4" -> Json.toJson(
            Map(
              "nested-collection"  -> Json.toJson(List(4, 5, 6)),
              "another-value-type" -> Json.toJson(99)
            ))
        ))
      val saved = TestObject("jsValueTest", jsValue = Some(unknownStructure))

      await(repository.insert(saved))

      val result: Option[TestObject] = await(repository.findById(saved.id))
      result should not be None

      val found = await(repository.find("jsValue.key1" -> "top level value"))

      found      should not be empty
      found.size shouldBe 1

      found.head.id shouldBe saved.id
    }
  }

  "Counting elements" should {
    "work if no specific query is passed" in {
      val existingDocuments = List.fill(2)(TestObject(gen[String]))

      val chain = for {
        _     <- repository.bulkInsert(existingDocuments)
        count <- repository.count
      } yield count

      await(chain) shouldBe existingDocuments.size
    }

    "consider a subset of documents based on a passed query" in {
      val now   = LocalDate.now(DateTimeZone.UTC)
      val later = now.plusDays(2)

      val currentDocuments = List.fill(2)(TestObject(aField  = gen[String], date = now))
      val futureDocuments  = List.fill(10)(TestObject(aField = gen[String], date = later))

      val chain = for {
        _     <- repository.bulkInsert(currentDocuments ++ futureDocuments)
        count <- repository.count(Json.obj("date" -> Json.obj("$gt" -> now.plusDays(1))))
      } yield count

      await(chain) shouldBe futureDocuments.size
    }
  }

  "Location field" should {

    "be stored" in {

      val coordinates: (Double, Double) = (51.512787, -0.090796)
      val saved                         = TestObject("storing a tuple2", location = coordinates)

      await(repository.insert(saved))

      val result: Option[TestObject] = await(repository.findById(saved.id))
      result should not be None

      result.get.location shouldBe coordinates
    }
  }

  "Bulk insert" should {
    val now = LocalDate.now(DateTimeZone.UTC)
    val objects = Seq(
      TestObject("firstItem", Some("1"), Some(List(NestedModel("a", "b"))), date  = now),
      TestObject("secondItem", Some("2"), Some(List(NestedModel("c", "d"))), date = now.plusDays(1)),
      TestObject("thirdItem", Some("3"), Some(List(NestedModel("e", "f"))), date  = now.plusDays(2)),
      TestObject("fourthItem", Some("4"), Some(List(NestedModel("g", "h"))), date = now.plusDays(3)),
      TestObject("fifthItem", Some("5"), Some(List(NestedModel("i", "j"))), date  = now.plusDays(4))
    )

    "insert all entities supplied" in {
      await(repository.bulkInsert(objects))
      val result = await(repository.findAll())
      result shouldBe objects
    }
  }
}

trait LogCapturing {

  import scala.collection.JavaConverters._
  import scala.reflect._

  def withCaptureOfLoggingFrom[T: ClassTag](body: (=> List[ILoggingEvent]) => Any): Any = {
    val logger = LoggerFactory.getLogger(classTag[T].runtimeClass).asInstanceOf[LogbackLogger]
    withCaptureOfLoggingFrom(logger)(body)
  }

  def withCaptureOfLoggingFrom(logger: LogbackLogger)(body: (=> List[ILoggingEvent]) => Any): Any = {
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(logger.getLoggerContext)
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.ALL)
    logger.setAdditive(true)
    body(appender.list.asScala.toList)
  }
}

case class NestedModel(a: String, b: String)

case class TestObject(
  aField: String,
  anotherField: Option[String]                                             = None,
  optionalCollection: Option[List[NestedModel]]                            = None,
  nestedMapOfCollections: Map[String, List[Map[String, Seq[NestedModel]]]] = Map.empty,
  modifiedDetails: CreationAndLastModifiedDetail                           = CreationAndLastModifiedDetail(),
  jsValue: Option[JsValue]                                                 = None,
  location: (Double, Double)                                               = (0.0, 0.0),
  date: LocalDate                                                          = LocalDate.now(DateTimeZone.UTC),
  id: BSONObjectID                                                         = BSONObjectID.generate) {

  def markUpdated(implicit updatedTime: DateTime): TestObject = copy(
    modifiedDetails = modifiedDetails.updated(updatedTime)
  )
}

object TestObject {
  implicit val formats: Format[TestObject] = mongoEntity {
    implicit val locationFormat: Format[(Double, Double)] = TupleFormats.tuple2Format[Double, Double]
    implicit val nestedModelformats: OFormat[NestedModel] = Json.format[NestedModel]
    Json.format[TestObject]
  }
}

class SimpleTestRepository(implicit mc: MongoConnector, ec: ExecutionContext)
    extends ReactiveRepository[TestObject, BSONObjectID](
      collectionName = "simpleTestRepository",
      mongo          = mc.db,
      domainFormat   = TestObject.formats,
      idFormat       = ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    Index(Seq("aField"       -> IndexType.Ascending), name = Some("aFieldUniqueIdx"), unique = true, sparse = true),
    Index(Seq("anotherField" -> IndexType.Ascending), name = Some("anotherFieldIndex"))
  )
}

class FailingIndexesTestRepository(implicit mc: MongoConnector, ec: ExecutionContext)
    extends ReactiveRepository[TestObject, BSONObjectID](
      "failingIndexesTestRepository",
      mc.db,
      TestObject.formats,
      ReactiveMongoFormats.objectIdFormats) {

  def indexName = "index1"

  override def indexes = Seq(
    Index(Seq("aField" -> IndexType.Ascending), name = Some(indexName), unique = true, sparse = true)
  )
}
