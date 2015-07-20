package uk.gov.hmrc.mongo

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.json.{ReactiveMongoFormats, TupleFormats}

case class NestedModel(a: String, b: String)

case class TestObject(aField: String,
                      anotherField: Option[String] = None,
                      optionalCollection: Option[List[NestedModel]] = None,
                      nestedMapOfCollections: Map[String, List[Map[String, Seq[NestedModel]]]] = Map.empty,
                      modifiedDetails: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
                      jsValue: Option[JsValue] = None,
                      location: Tuple2[Double, Double] = (0.0, 0.0),
                      date: LocalDate = LocalDate.now(DateTimeZone.UTC),
                      id: BSONObjectID = BSONObjectID.generate) {

  def markUpdated(implicit updatedTime: DateTime) = copy(
    modifiedDetails = modifiedDetails.updated(updatedTime)
  )

}

object TestObject {

  import ReactiveMongoFormats.{objectIdFormats, localDateFormats, mongoEntity}

  implicit val formats = mongoEntity {

    implicit val locationFormat = TupleFormats.tuple2Format[Double, Double]

    implicit val nestedModelformats = Json.format[NestedModel]

    Json.format[TestObject]
  }
}

class SimpleTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[TestObject, BSONObjectID]("simpleTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    Index(Seq("aField" -> IndexType.Ascending), name = Some("aFieldUniqueIdx"), unique = true, sparse = true),
    Index(Seq("anotherField" -> IndexType.Ascending), name = Some("anotherFieldIndex"))
  )
}

class FailingIndexesTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[TestObject, BSONObjectID]("failingIndexesTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    Index(Seq("aField" -> IndexType.Ascending), name = Some("index1"), unique = true, sparse = true)
  )
}

class ReactiveRepositorySpec extends WordSpec with Matchers with MongoSpecSupport with BeforeAndAfterEach with Awaiting with CurrentTime with Eventually with LogCapturing {

  val repository = new SimpleTestRepository
  val uniqueIndexRepository = new FailingIndexesTestRepository

  override def beforeEach() {
    await(repository.removeAll())
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

      val result: List[TestObject] = await(repository.findAll())
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

  "removeById" should {

    "remove the identified record" in {
      val e1 = TestObject("1")

      await(repository.save(e1))

      await(repository.removeById(e1.id)).inError shouldBe false

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should be(None)
    }
  }


  "remove with provided query" should {

    "remove by one field" in {
      val e1 = TestObject("1", Some("used to identify"))

      await(repository.save(e1))

      await(repository.remove("anotherField" -> "used to identify")).inError shouldBe false

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should be(None)
    }

    "remove by multiple field query" in {

      import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.objectIdFormats

      val e1 = TestObject("1", Some("used to identify"))

      await(repository.save(e1))

      await(repository.remove("_id" -> e1.id, "anotherField" -> "used to identify")).inError shouldBe false

      val result: Option[TestObject] = await(repository.findById(e1.id))
      result should be(None)
    }
  }

  "Creation of Indexes" should {
    "should be done based on provided Indexes" in new LogCapturing {
      await(repository.drop)
      await(repository.collection.indexesManager.list()) shouldBe empty

      await(repository.ensureIndexes)
      await(repository.save(TestObject("random_object")))

      val indexes = await(repository.collection.indexesManager.list()).map(f => f.name.getOrElse(""))
      indexes should contain("aFieldUniqueIdx")
      indexes should contain("anotherFieldIndex")
    }

    "mean that exceptions are thrown when saving a duplicate record on a unique index" in {
      val uniqueField = "i_am_a_unique_field"
      val saveWithoutError = TestObject(uniqueField)
      val shouldNotSave = TestObject(uniqueField)

      await(repository.drop)
      await(repository.ensureIndexes)

      await(repository.save(saveWithoutError))

      a [DatabaseException] should be thrownBy await(repository.save(shouldNotSave))
    }

    "should not log errors when all are created successfully" in  {
      withCaptureOfLoggingFrom[SimpleTestRepository] { logList =>
        await(repository.drop)
        await(repository.ensureIndexes)
        await(repository.save(TestObject("random_object")))

        eventually(timeout(Span(5, Seconds))) {
          logList shouldBe empty
        }
      }
    }

    "should log any error that arises when creating an index" in  {
      withCaptureOfLoggingFrom[FailingIndexesTestRepository] { logList =>
        await(uniqueIndexRepository.drop)

        await(uniqueIndexRepository.save(TestObject("uniqueKey", Some("bogus"))))
        await(uniqueIndexRepository.save(TestObject("uniqueKey", Some("whatever"))))

        await(uniqueIndexRepository.ensureIndexes) shouldBe Seq(false)
        logList.size should be(1)
        logList.head.getMessage shouldBe (uniqueIndexRepository.message)

      }
    }
  }

  "Storing a raw JsValue" should {
    "be able to be queried" in {

      val unknownStructure = Json.toJson(Map(
        "key1" -> Json.toJson("top level value"),
        "key2" -> Json.toJson(List(1, 2, 3)),
        "key3" -> Json.toJson(42.0),
        "key4" -> Json.toJson(Map(
          "nested-collection" -> Json.toJson(List(4, 5, 6)),
          "another-value-type" -> Json.toJson(99)
        ))
      ))
      val saved = TestObject("jsValueTest", jsValue = Some(unknownStructure))

      await(repository.save(saved))

      val result: Option[TestObject] = await(repository.findById(saved.id))
      result should not be None

      val found = await(repository.find("jsValue.key1" -> "top level value"))

      found should not be empty
      found.size shouldBe 1

      found.head.id shouldBe saved.id
    }
  }


  "Location field" should {

    "be stored" in {

      val coordinates: Tuple2[Double, Double] = (51.512787, -0.090796)
      val saved = TestObject("storing a tuple2", location = coordinates)

      await(repository.save(saved))

      val result: Option[TestObject] = await(repository.findById(saved.id))
      result should not be None

      result.get.location shouldBe coordinates
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
