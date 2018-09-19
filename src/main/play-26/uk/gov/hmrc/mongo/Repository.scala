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

package uk.gov.hmrc.mongo

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.JsObject
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import reactivemongo.play.json.commands.JSONFindAndModifyCommand._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait Indexes {
  def indexes: Seq[Index] = Seq.empty
}

sealed abstract class UpdateType[A] {
  def savedValue: A
}

case class Saved[A](savedValue: A) extends UpdateType[A]

case class Updated[A](previousValue: A, savedValue: A) extends UpdateType[A]

case class DatabaseUpdate[A](writeResult: LastError, updateType: UpdateType[A])

trait CurrentTime {

  protected lazy val zone: DateTimeZone = DateTimeZone.UTC

  def withCurrentTime[A](f: (DateTime) => A) = f(DateTime.now.withZone(zone))
}

trait Repository[A, ID] extends CurrentTime {

  import reactivemongo.api.ReadPreference

  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(
    implicit ec: ExecutionContext): Future[List[A]]

  def findById(id: ID, readPreference: ReadPreference = ReadPreference.primaryPreferred)(
    implicit ec: ExecutionContext): Future[Option[A]]

  def find(query: (String, play.api.libs.json.Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]]

  /**
    * Updates and returns a single document.
    *
    * See here for more details: https://docs.mongodb.com/manual/reference/method/db.collection.findAndModify
    *
    * @param fetchNewObject - corresponds to 'new' described in mongo documentation linked above.
    */
  def findAndUpdate(
    query: JsObject,
    update: JsObject,
    fetchNewObject: Boolean           = false,
    upsert: Boolean                   = false,
    sort: Option[JsObject]            = None,
    fields: Option[JsObject]          = None,
    bypassDocumentValidation: Boolean = false,
    writeConcern: WriteConcern        = WriteConcern.Default,
    maxTime: Option[FiniteDuration]   = None,
    collation: Option[Collation]      = None,
    arrayFilters: Seq[JsObject]       = Nil)(implicit ec: ExecutionContext): Future[FindAndModifyResult]

  def count(implicit ec: ExecutionContext): Future[Int]

  def count(query: JsObject, readPreference: ReadPreference = ReadPreference.primary)(
    implicit ec: ExecutionContext): Future[Int]

  def removeAll(writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult]

  def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(
    implicit ec: ExecutionContext): Future[WriteResult]

  def remove(query: (String, play.api.libs.json.Json.JsValueWrapper)*)(
    implicit ec: ExecutionContext): Future[WriteResult]

  def drop(implicit ec: ExecutionContext): Future[Boolean]

  @deprecated("use ReactiveRepository#insert() instead", "3.0.1")
  def save(entity: A)(implicit ec: ExecutionContext): Future[WriteResult] = insert(entity)

  def insert(entity: A)(implicit ec: ExecutionContext): Future[WriteResult]

  def bulkInsert(entities: Seq[A])(implicit ec: ExecutionContext): Future[MultiBulkWriteResult]

}
