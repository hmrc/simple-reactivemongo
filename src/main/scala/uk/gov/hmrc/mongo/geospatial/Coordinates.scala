/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsArray, Writes}
import uk.gov.hmrc.mongo.json.TupleFormats

case class Coordinates(lon: Double, lat: Double) {
  lazy val tuple = (lon, lat)
}

object Coordinates {

  val reads = TupleFormats.tuple2Reads[Coordinates, Double, Double](Coordinates.apply _)

  def writes(implicit aWrites: Writes[Double]) = new Writes[Coordinates] {
    def writes(coordinates: Coordinates) =
      JsArray(Seq(aWrites.writes(coordinates.lon), aWrites.writes(coordinates.lat)))
  }

  implicit val formats = Format(reads, writes)

}
