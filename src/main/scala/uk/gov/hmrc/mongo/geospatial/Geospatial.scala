package uk.gov.hmrc.mongo.geospatial

import reactivemongo.api.ReadPreference
import uk.gov.hmrc.mongo.ReactiveRepository

trait Geospatial[A, ID] {
  self: ReactiveRepository[A, ID] =>

  import scala.concurrent.ExecutionContext
  import play.api.libs.json.Json
  import reactivemongo.api.indexes.Index
  import reactivemongo.api.indexes.IndexType.Geo2DSpherical
  import reactivemongo.play.json.ImplicitBSONHandlers._

  lazy val LocationField = "loc"

  lazy val geo2DSphericalIndex = Index(Seq((LocationField, Geo2DSpherical)), Some("geo2DSphericalIdx"))

  def nearPoint(
    lon: Double,
    lat: Double,
    limit: Int                     = 100,
    readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext) =
    collection
      .find(
        Json.obj(
          LocationField -> Json.obj(
            "$near" -> Json.obj(
              "$geometry" -> Json.obj(
                "type"        -> "Point",
                "coordinates" -> Json.arr(lon, lat)
              )
            )
          )
        )
      )
      .cursor[A](readPreference)
      .collect[List](limit)
}
