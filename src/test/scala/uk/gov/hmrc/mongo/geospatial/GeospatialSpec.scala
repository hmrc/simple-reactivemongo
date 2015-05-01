package uk.gov.hmrc.mongo.geospatial

import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.{Awaiting, MongoSpecSupport, ReactiveRepository, MongoConnector}
import uk.gov.hmrc.mongo.json.{BSONObjectIdFormats, ReactiveMongoFormats}

import scala.concurrent.{Future, ExecutionContext}

case class Place(loc : Coordinates, id: BSONObjectID = BSONObjectID.generate)
object Place{

  val formats = ReactiveMongoFormats.mongoEntity({
    import Coordinates.formats
    import BSONObjectIdFormats._

    Json.format[Place]
  })
}


class GeospatialTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[Place, BSONObjectID]("geospatialTestRepository", mc.db, Place.formats, ReactiveMongoFormats.objectIdFormats)
  with Geospatial[Place, BSONObjectID]{
  override val updateExistingIndexes: Boolean = false
  override def indexes = Seq(geo2DSphericalIndex)

}


class LegacyGeospatialSpec extends WordSpec with Matchers with MongoSpecSupport with BeforeAndAfterEach with Awaiting {

  private implicit def tupleToPlace(t : (Double, Double)) = Place(Coordinates(t._1, t._2))

  private val geospatialRepository = new GeospatialTestRepository

  val aldgate = (-0.0770733, 51.5134224)
  val stPauls = (-0.0974016, 51.5146721)
  val barbican = (-0.090796, 51.512787)
  val londonBridge = (-0.0906243, 51.5038924)


  override def beforeEach() = {
    await(geospatialRepository.drop)
    await(geospatialRepository.ensureIndexes)
  }

  "2D index near" should {


    "find the nearest london tube station" in {

      await(geospatialRepository.save(aldgate))
      await(geospatialRepository.save(stPauls))
      await(geospatialRepository.save(londonBridge))
      await(geospatialRepository.save(barbican))

      val tateModern = Coordinates(-0.0989392, 51.5081062)

      val nearResults = await(geospatialRepository.nearPoint(tateModern.lon, tateModern.lat))

      nearResults should not be empty

      nearResults(0).loc.tuple shouldBe stPauls
      nearResults(1).loc.tuple shouldBe londonBridge
      nearResults(2).loc.tuple shouldBe barbican
      nearResults(3).loc.tuple shouldBe aldgate
    }

    "find a limited number" in {

      val aldgateNotInBatch : Place = aldgate
      await(geospatialRepository.save(aldgateNotInBatch))

      await(geospatialRepository.save(stPauls))
      await(geospatialRepository.save(londonBridge))

      val barbicanNotInBatch : Place = barbican
      await(geospatialRepository.save(barbicanNotInBatch))

      val tateModern = Coordinates(-0.0989392, 51.5081062)

      val nearResults = await(geospatialRepository.nearPoint(tateModern.lon, tateModern.lat, 2))

      nearResults should not be empty

      nearResults.size shouldBe 2
      nearResults(0).loc.tuple shouldBe stPauls
      nearResults(1).loc.tuple shouldBe londonBridge

      nearResults should not contain barbicanNotInBatch
      nearResults should not contain aldgateNotInBatch
    }

  }
}
