package uk.gov.hmrc.mongo.geospatial

import play.api.libs.json.{Format, JsArray, Writes}
import uk.gov.hmrc.mongo.json.TupleFormats

case class Coordinates(lon : Double, lat : Double) {
  lazy val tuple = (lon, lat)
}

object Coordinates {

  val reads = TupleFormats.tuple2Reads[Coordinates, Double, Double](Coordinates.apply _)

  def writes(implicit aWrites: Writes[Double]) = new Writes[Coordinates] {
    def writes(coordinates: Coordinates) = JsArray(Seq(aWrites.writes(coordinates.lon), aWrites.writes(coordinates.lat)))
  }

  implicit val formats = Format(reads, writes)

}
