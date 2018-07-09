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

package uk.gov.hmrc.mongo.geospatial

import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.{Awaiting, MongoConnector, MongoSpecSupport, ReactiveRepository}
import uk.gov.hmrc.mongo.json.{BSONObjectIdFormats, ReactiveMongoFormats}

import scala.concurrent.{ExecutionContext, Future}

case class Place(loc: Coordinates, id: BSONObjectID = BSONObjectID.generate)
object Place {

  val formats = ReactiveMongoFormats.mongoEntity({
    import ReactiveMongoFormats.objectIdFormats

    Json.format[Place]
  })
}

class GeospatialTestRepository(implicit mc: MongoConnector, ec: ExecutionContext)
    extends ReactiveRepository[Place, BSONObjectID](
      "geospatialTestRepository",
      mc.db,
      Place.formats,
      ReactiveMongoFormats.objectIdFormats)
    with Geospatial[Place, BSONObjectID] {

  override def indexes = Seq(geo2DSphericalIndex)

}

class LegacyGeospatialSpec extends WordSpec with Matchers with MongoSpecSupport with BeforeAndAfterEach with Awaiting {

  private implicit def tupleToPlace(t: (Double, Double)) = Place(Coordinates(t._1, t._2))

  private val geospatialRepository = new GeospatialTestRepository

  val aldgate      = (-0.0770733, 51.5134224)
  val stPauls      = (-0.0974016, 51.5146721)
  val barbican     = (-0.090796, 51.512787)
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

      val aldgateNotInBatch: Place = aldgate
      await(geospatialRepository.save(aldgateNotInBatch))

      await(geospatialRepository.save(stPauls))
      await(geospatialRepository.save(londonBridge))

      val barbicanNotInBatch: Place = barbican
      await(geospatialRepository.save(barbicanNotInBatch))

      val tateModern = Coordinates(-0.0989392, 51.5081062)

      val nearResults = await(geospatialRepository.nearPoint(tateModern.lon, tateModern.lat, 2))

      nearResults should not be empty

      nearResults.size         shouldBe 2
      nearResults(0).loc.tuple shouldBe stPauls
      nearResults(1).loc.tuple shouldBe londonBridge

      nearResults should not contain barbicanNotInBatch
      nearResults should not contain aldgateNotInBatch
    }

  }
}
