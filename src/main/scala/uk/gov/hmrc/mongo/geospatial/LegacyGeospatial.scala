/*
 * Copyright 2014 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.hmrc.mongo.geospatial

import play.api.libs.json._
import scala.Some
import reactivemongo.api.QueryOpts
import uk.gov.hmrc.mongo.ReactiveRepository


/*
 * The 2d index supports data stored as legacy coordinate pairs and is intended for use in MongoDB 2.2 and earlier.
 *
 * see http://docs.mongodb.org/manual/applications/geospatial-indexes/
 */
trait LegacyGeospatial[A, ID] {
  self: ReactiveRepository[A, ID] =>

  import reactivemongo.api.indexes.IndexType.Geo2D
  import reactivemongo.api.indexes.Index

  lazy val LocationField = "loc"

  lazy val geo2DIndex = Index(Seq((LocationField, Geo2D)), Some("geo2DIdx"))

  def near(lon: Double, lat: Double, limit: Int = 100) = collection.find(
    Json.obj(
      LocationField -> Json.obj(
        "$near" -> Json.arr(lon, lat)
      )
    )
  ).options(QueryOpts(batchSizeN = limit)).cursor[A].collect[List]()

}