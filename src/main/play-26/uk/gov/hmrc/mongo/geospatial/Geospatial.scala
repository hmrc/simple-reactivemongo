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

import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.Future

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
    readPreference: ReadPreference = ReadPreference.primaryPreferred
  )(implicit ec: ExecutionContext): Future[List[A]] =
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
      .collect[List](limit, FailOnError[List[A]]())
}
