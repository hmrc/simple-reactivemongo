package uk.gov.hmrc.mongo

import scala.concurrent.Future
import org.scalatest.{ BeforeAndAfterAll, Matchers, FeatureSpec }
import org.scalatest.concurrent.Eventually
import reactivemongo.core.commands.LastError
import reactivemongo.bson.BSONObjectID
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

  feature("findAll paging") {

    scenario("page 1, page size 1") {
      val results = await(repository.findAllPaged(1, 1))
      results.map(_.aField) should be(Seq("1"))
    }

    scenario("page 1, page size 5") {
      val results = await(repository.findAllPaged(5, 1))
      results.map(_.aField) should be(Seq("1", "2", "3", "4", "5"))
    }

    scenario("page 2, page size 1") {
      val results = await(repository.findAllPaged(1, 2))
      results.map(_.aField) should be(Seq("2"))
    }

    scenario("page 5, page size 20") {
      val results = await(repository.findAllPaged(20, 5))
      results.map(_.aField) should be(Seq(
        "81", "82", "83", "84", "85", "86", "87", "88", "89",
        "90", "91", "92", "93", "94", "95", "96", "97", "98",
        "99", "100"))
    }

    scenario("zero page size returns empty search results") {
      await(repository.findAllPaged(0, 10)) should be(Seq.empty)
    }

    scenario("page beyond valid results returns empty search results") {
      await(repository.findAllPaged(30, 5)) should be(Seq.empty)
    }

    scenario("Non positive page number failure") {
      intercept[reactivemongo.core.errors.DetailedDatabaseException] {
        await(repository.findAllPaged(20, 0))
      }
    }

    scenario("Non positive pageSize failure") {
      intercept[reactivemongo.core.errors.DetailedDatabaseException] {
        await(repository.findAllPaged(-1, 10))
      }
    }

  }

  feature("find paging") {

    scenario("TODO") {
      // TODO 
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

    def clearTestDataFromMongo() {
      await(repository.removeAll)
    }

    class PagingTestsRepository(implicit mc: MongoConnector) extends ReactiveRepository[TestObject, BSONObjectID](
      "pagingTestsRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {}
  }
}