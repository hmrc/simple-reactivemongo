/*
 * Copyright 2015 HM Revenue & Customs
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

package uk.gov.hmrc.mongo.json

import play.api.libs.json._
import org.joda.time.{LocalDate, DateTimeZone, DateTime, LocalDateTime}
import reactivemongo.bson.BSONObjectID

object ReactiveMongoFormats extends ReactiveMongoFormats

trait ReactiveMongoFormats {

  implicit val localDateRead: Reads[LocalDate] =
    (__ \ "$date").read[Long].map { date => new LocalDate(date, DateTimeZone.UTC) }


  implicit val localDateWrite: Writes[LocalDate] = new Writes[LocalDate] {
    def writes(date: LocalDate): JsValue = Json.obj(
      "$date" -> date.toDateTimeAtStartOfDay(DateTimeZone.UTC).getMillis
    )
  }

  implicit val localDateTimeRead: Reads[LocalDateTime] =
    (__ \ "$date").read[Long].map { dateTime => new LocalDateTime(dateTime, DateTimeZone.UTC) }


  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = Json.obj(
      "$date" -> dateTime.toDateTime(DateTimeZone.UTC).getMillis
    )
  }

  implicit val dateTimeRead: Reads[DateTime] =
    (__ \ "$date").read[Long].map { dateTime =>
      new DateTime(dateTime, DateTimeZone.UTC)
    }


  implicit val dateTimeWrite: Writes[DateTime] = new Writes[DateTime] {
    def writes(dateTime: DateTime): JsValue = Json.obj(
      "$date" -> dateTime.getMillis
    )
  }

  implicit val objectIdRead: Reads[BSONObjectID] =
    (__ \ "$oid").read[String].map { oid =>
      BSONObjectID(oid)
    }


  implicit val objectIdWrite: Writes[BSONObjectID] = new Writes[BSONObjectID] {
    def writes(oid: BSONObjectID): JsValue = Json.obj(
      "$oid" -> oid.stringify
    )
  }

  implicit val objectIdFormats = Format(objectIdRead, objectIdWrite)
  implicit val dateTimeFormats = Format(dateTimeRead, dateTimeWrite)
  implicit val localDateFormats = Format(localDateRead, localDateWrite)
  implicit val localDateTimeFormats = Format(localDateTimeRead, localDateTimeWrite)


  def mongoEntity[A](baseFormat: Format[A]) : Format[A] = {
    import JsonExtensions._
    val publicIdPath: JsPath = JsPath \ '_id
    val privateIdPath: JsPath = JsPath \ 'id
    new Format[A] {
      def reads(json: JsValue): JsResult[A] = baseFormat.compose(copyKey(publicIdPath, privateIdPath)).reads(json)

      def writes(o: A): JsValue = baseFormat.transform(moveKey(privateIdPath,publicIdPath)).writes(o)
    }
  }
}
