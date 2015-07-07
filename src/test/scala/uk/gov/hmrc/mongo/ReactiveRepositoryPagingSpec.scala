package uk.gov.hmrc.mongo

import scala.concurrent.Future
import org.scalatest.{ BeforeAndAfterAll, Matchers, FeatureSpec }
import org.scalatest.concurrent.Eventually
import reactivemongo.core.commands.LastError
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.{ JsArray, JsString, JsObject, Json }
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

class ReactiveRepositoryPagingSpec extends FeatureSpec with Matchers with MongoSpecSupport
  with Awaiting with Eventually with LogCapturing with BeforeAndAfterAll {

  import TestData._

  override def beforeAll() {
    populateMongoWithTestData()
  }

  override def afterAll() {
    clearTestDataFromMongo()
  }

  feature("findAll with paging") {

    scenario("returns the 1st result using ID ordering for page size 1 page 1") {
      val results = await(repository.findAllPaged(1, 1))
      results.map(_.aField) should be(Seq("1"))
    }

    scenario("returns the first 5 results using ID ordering for a page size 5 page 1") {
      val results = await(repository.findAllPaged(5, 1))
      results.map(_.aField) should be(Seq("1", "2", "3", "4", "5"))
    }

    scenario("returns the 2nd result using ID ordering for page size 1 page 2") {
      val results = await(repository.findAllPaged(1, 2))
      results.map(_.aField) should be(Seq("2"))
    }

    scenario("returns results 81 - 100 using ID ordering for page size 20 page 5") {
      val results = await(repository.findAllPaged(20, 5))
      results.map(_.aField) should be(Seq(
        "81", "82", "83", "84", "85", "86", "87", "88", "89",
        "90", "91", "92", "93", "94", "95", "96", "97", "98",
        "99", "100")
      )
    }

    scenario("zero page size returns empty search results") {
      await(repository.findAllPaged(0, 10)) should be(Seq.empty)
    }

    scenario("page beyond end of results returns empty search results") {
      await(repository.findAllPaged(30, 5)) should be(Seq.empty)
    }

    scenario("Non positive page number causes error") {
      intercept[reactivemongo.core.errors.DetailedDatabaseException] {
        await(repository.findAllPaged(20, 0))
      }
    }

    scenario("Non positive pageSize causes error") {
      intercept[reactivemongo.core.errors.DetailedDatabaseException] {
        await(repository.findAllPaged(-1, 10))
      }
    }

  }

  feature("find with paging") {

    scenario("returns first item matching query using ID ordering for page size 1 page 1") {
      val query = or("aField", Seq(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
      )
      val aFields = await(repository.findPaged(1, 1, query)).map(_.aField)
      aFields should be(Seq("1"))
    }

    scenario("returns items 5 and 6 matching querying using ID ordering for page size 2 page 3") {
      val query = or("aField", Seq(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
      )
      val aFields = await(repository.findPaged(2, 3, query)).map(_.aField)
      aFields should be(Seq("5", "6"))
    }

    scenario("zero page size returns empty search results") {
      val query = or("aField", Seq("1"))
      await(repository.findPaged(0, 2, query)) should be(Seq.empty)
    }

    scenario("page beyond end of search results returns empty search results") {
      val query = or("aField", Seq("1"))
      await(repository.findPaged(2, 2, query)) should be(Seq.empty)
    }

    scenario("Non positive page number causes error") {
      val query = or("aField", Seq("1"))
      intercept[reactivemongo.core.errors.DetailedDatabaseException] {
        await(repository.findPaged(5, -1, query))
      }
    }

    scenario("Non positive page size causes error") {
      val query = or("aField", Seq("1"))
      intercept[reactivemongo.core.errors.DetailedDatabaseException] {
        await(repository.findPaged(-1, 10, query))
      }
    }
  }

  object TestData {
    val repository = new PagingTestsRepository

    def populateMongoWithTestData() {
      for (n <- (1 to 100)) {
        val oId = padTo24Chars(n)
        val o = TestObject(n.toString, id = BSONObjectID(oId))
        await(repository.save(o))
      }
    }

    private def padTo24Chars(n: Int): String = {
      val s = n.toString
      val zeros = 24 - s.length
      val zs = (1 to zeros).fold("")((st, n) => s"${st}0")
      s"${zs}$s"
    }

    def or(fieldName: String, values: Seq[String]) = {
      val fieldValues = values.map { v =>
        JsObject(Seq((fieldName, JsString(v))))
      }
      ("$or", Json.toJsFieldJsValueWrapper(JsArray(fieldValues)))
    }

    def clearTestDataFromMongo() {
      await(repository.removeAll)
    }

    class PagingTestsRepository(implicit mc: MongoConnector) extends ReactiveRepository[TestObject, BSONObjectID](
      "pagingTestsRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {}
  }
}