/*
 * Copyright 2019 HM Revenue & Customs
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

import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.core.errors.GenericDatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONBatchCommands.JSONCountCommand._
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.commands.JSONFindAndModifyCommand.{FindAndModifyResult, Update}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

abstract class ReactiveRepository[A, ID](
  protected[mongo] val collectionName: String,
  protected[mongo] val mongo: () => DB,
  domainFormat: Format[A],
  idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats)
    extends Indexes
    with MongoDb
    with CollectionName
    with CurrentTime {

  import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper

  implicit val domainFormatImplicit: Format[A] = domainFormat
  implicit val idFormatImplicit: Format[ID]    = idFormat

  lazy val collection: JSONCollection = mongo().collection[JSONCollection](collectionName)

  protected[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val message: String                = "Failed to ensure index"

  ensureIndexes(scala.concurrent.ExecutionContext.Implicits.global)

  protected val _Id                   = "_id"
  protected def _id(id: ID): JsObject = Json.obj(_Id -> id)

  def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] =
    collection
      .find(Json.obj(query: _*))
      .cursor[A](ReadPreference.primaryPreferred)
      .collect(maxDocs = -1, FailOnError[List[A]]())

  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(
    implicit ec: ExecutionContext): Future[List[A]] =
    collection
      .find(Json.obj())
      .cursor[A](readPreference)
      .collect(maxDocs = -1, FailOnError[List[A]]())

  def findById(id: ID, readPreference: ReadPreference = ReadPreference.primaryPreferred)(
    implicit ec: ExecutionContext): Future[Option[A]] =
    collection.find(_id(id)).one[A](readPreference)

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
    arrayFilters: Seq[JsObject]       = Nil)(implicit ec: ExecutionContext): Future[FindAndModifyResult] =
    collection.findAndModify(
      selector                 = query,
      modifier                 = Update(update, fetchNewObject, upsert),
      sort                     = sort,
      fields                   = fields,
      bypassDocumentValidation = bypassDocumentValidation,
      writeConcern             = writeConcern,
      maxTime                  = maxTime,
      collation                = collation,
      arrayFilters             = arrayFilters
    )

  def count(implicit ec: ExecutionContext): Future[Int] = count(Json.obj())

  def count(query: JsObject, readPreference: ReadPreference = ReadPreference.primary)(
    implicit ec: ExecutionContext): Future[Int] =
    collection
      .runCommand(Count(ImplicitlyDocumentProducer.producer(query)), readPreference)
      .map(_.count)

  def removeAll(writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult] =
    collection.delete(ordered = true, writeConcern).one(Json.obj(), None)

  def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(
    implicit ec: ExecutionContext): Future[WriteResult] =
    collection.delete(ordered = true, writeConcern).one(_id(id), Some(1))

  def remove(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[WriteResult] =
    collection.delete().one(Json.obj(query: _*)) //TODO: pass in the WriteConcern

  def drop(implicit ec: ExecutionContext): Future[Boolean] =
    collection
      .drop(failIfNotFound = true)
      .map(_ => true)
      .recover[Boolean] {
        case _ => false
      }

  @deprecated("use ReactiveRepository#insert() instead", "3.0.1")
  def save(entity: A)(implicit ec: ExecutionContext): Future[WriteResult] = insert(entity)

  def insert(entity: A)(implicit ec: ExecutionContext): Future[WriteResult] =
    domainFormat.writes(entity) match {
      case d @ JsObject(_) => collection.insert(d)
      case _ =>
        Future.failed[WriteResult](new Exception("cannot write object") with NoStackTrace)
    }

  def bulkInsert(entities: Seq[A])(
    implicit ec: ExecutionContext
  ): Future[MultiBulkWriteResult] = {
    val docs           = entities.map(toJsObject)
    val failures       = docs.collect { case Left(f) => f }
    lazy val successes = docs.collect { case Right(x) => x }
    if (failures.isEmpty) {
      collection.insert(ordered = false)(implicitly[collection.pack.Writer[JsObject]]).many(successes)
    } else {
      Future.failed[MultiBulkWriteResult](new BulkInsertRejected())
    }
  }

  private def toJsObject(entity: A) = domainFormat.writes(entity) match {
    case j: JsObject => Right(j)
    case _           => Left(entity)
  }

  class BulkInsertRejected extends Exception("No objects inserted. Error converting some or all to JSON")

  private def ensureIndex(index: Index)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.indexesManager
      .create(index)
      .map(wr => {
        if (!wr.ok) {
          val msg = wr.writeErrors.mkString(", ")
          val maybeMsg = if (msg.contains("E11000")) {
            // this is for backwards compatibility to mongodb 2.6.x
            throw GenericDatabaseException(msg, wr.code)
          } else Some(msg)
          logger.error(s"$message (${index.eventualName}) : '${maybeMsg.map(_.toString)}'")
        }
        wr.ok
      })
      .recover {
        case t =>
          logger.error(s"$message (${index.eventualName})", t)
          false
      }

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(indexes.map(ensureIndex))
}

sealed abstract class UpdateType[A] {
  def savedValue: A
}

case class Saved[A](savedValue: A) extends UpdateType[A]

case class Updated[A](previousValue: A, savedValue: A) extends UpdateType[A]

case class DatabaseUpdate[A](writeResult: LastError, updateType: UpdateType[A])

trait Indexes {
  def indexes: Seq[Index] = Seq.empty
}
