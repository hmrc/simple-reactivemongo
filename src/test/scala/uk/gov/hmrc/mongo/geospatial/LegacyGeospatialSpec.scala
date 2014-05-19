package uk.gov.hmrc.mongo.geospatial

import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.Json
import scala.concurrent.Future
import uk.gov.hmrc.mongo.{ReactiveRepository, MongoConnector, Awaiting, MongoSpecSupport}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class Place(loc : Coordinates, id: BSONObjectID = BSONObjectID.generate)
object Place{

  val formats = ReactiveMongoFormats.mongoEntity({
    import uk.gov.hmrc.mongo.json.BSONObjectIdFormats._

    Json.format[Place]
  })
}


class LegacyGeospatialTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[Place, BSONObjectID]("lagacyGeospatialTestRepository", mc.db, Place.formats, ReactiveMongoFormats.objectIdFormats)
  with LegacyGeospatial[Place, BSONObjectID]{

  override def ensureIndexes(): Future[_] = collection.indexesManager.ensure(geo2DIndex)
}


class LegacyGeospatialSpec extends WordSpec with Matchers with MongoSpecSupport with BeforeAndAfterEach with Awaiting {

  private implicit def tupleToPlace(t : (Double, Double)) = Place(Coordinates(t._1, t._2))

  private val lagacyGeospatialRepository = new LegacyGeospatialTestRepository

  val aldgate = (-0.0770733, 51.5134224)
  val stPauls = (-0.0974016, 51.5146721)
  val barbican = (-0.090796, 51.512787)
  val londonBridge = (-0.0906243, 51.5038924)


  override def beforeEach() = {
    await(lagacyGeospatialRepository.drop)
    await(lagacyGeospatialRepository.ensureIndexes())
  }

  "2D index near" should {


    "find the nearest london tube station" in {

      await(lagacyGeospatialRepository.save(aldgate))
      await(lagacyGeospatialRepository.save(stPauls))
      await(lagacyGeospatialRepository.save(londonBridge))
      await(lagacyGeospatialRepository.save(barbican))

      val tateModern = Coordinates(-0.0989392, 51.5081062)

      val nearResults = await(lagacyGeospatialRepository.near(tateModern.lon, tateModern.lat))

      nearResults should not be empty

      nearResults(0).loc.tuple shouldBe stPauls
      nearResults(1).loc.tuple shouldBe londonBridge
      nearResults(2).loc.tuple shouldBe barbican
      nearResults(3).loc.tuple shouldBe aldgate
    }

    "find a limited number" in {

      val aldgateNotInBatch : Place = aldgate
      await(lagacyGeospatialRepository.save(aldgateNotInBatch))

      await(lagacyGeospatialRepository.save(stPauls))
      await(lagacyGeospatialRepository.save(londonBridge))

      val barbicanNotInBatch : Place = barbican
      await(lagacyGeospatialRepository.save(barbicanNotInBatch))

      val tateModern = Coordinates(-0.0989392, 51.5081062)

      val nearResults = await(lagacyGeospatialRepository.near(tateModern.lon, tateModern.lat, 2))

      nearResults should not be empty

      nearResults.size shouldBe 2
      nearResults(0).loc.tuple shouldBe stPauls
      nearResults(1).loc.tuple shouldBe londonBridge

      nearResults should not contain barbicanNotInBatch
      nearResults should not contain aldgateNotInBatch
    }

  }
}
