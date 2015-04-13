package uk.gov.hmrc.mongo

import org.joda.time.{DateTimeZone, DateTime}
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class CreationAndLastModifiedDetail(createdAt: DateTime = DateTime.now.withZone(DateTimeZone.UTC),
                                         lastUpdated: DateTime = DateTime.now.withZone(DateTimeZone.UTC)) {

  def updated(updatedTime: DateTime) = copy(
    lastUpdated = updatedTime
  )
}

object CreationAndLastModifiedDetail {
  import ReactiveMongoFormats.{dateTimeRead, dateTimeWrite}
  implicit val formats = Json.format[CreationAndLastModifiedDetail]

  def withTime(time: DateTime) = new CreationAndLastModifiedDetail(
    createdAt = time,
    lastUpdated = time
  )
}
