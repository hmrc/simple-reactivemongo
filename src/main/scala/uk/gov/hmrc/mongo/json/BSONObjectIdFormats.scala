package uk.gov.hmrc.mongo.json

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

object BSONObjectIdFormats extends BSONObjectIdFormats

trait BSONObjectIdFormats {

  implicit val objectIdRead: Reads[BSONObjectID] = __.read[String].map { oid =>
    BSONObjectID(oid)
  }

  implicit val objectIdWrite: Writes[BSONObjectID] = new Writes[BSONObjectID] {
    def writes(oid: BSONObjectID): JsValue = JsString(oid.stringify)
  }

  implicit val objectIdFormats = Format(BSONObjectIdFormats.objectIdRead, BSONObjectIdFormats.objectIdWrite)

}
