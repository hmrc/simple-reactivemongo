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

package uk.gov.hmrc.mongo

import play.api.libs.json.{JsError, JsResultException, JsSuccess, Reads}
import reactivemongo.api.DB
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.LastError
import reactivemongo.api.commands.bson.BSONFindAndModifyCommand._
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectReader

import scala.concurrent.{ExecutionContext, Future}

@deprecated("Will be removed when AtomicUpdate trait is removed", "7.0.0")
trait MongoDb {
  protected[mongo] def mongo: () => DB
}

@deprecated("Will be removed when AtomicUpdate trait is removed", "7.0.0")
trait CollectionName {
  protected[mongo] def collectionName: String
}

@deprecated("Please use findAndUpdate method from ReactiveRepository instead", "7.0.0")
trait AtomicUpdate[T] extends CurrentTime with BSONBuilderHelpers with MongoDb with CollectionName {

  def isInsertion(newRecordId: BSONObjectID, oldRecord: T): Boolean

  private lazy val bsonCollection: BSONCollection = mongo().collection[BSONCollection](collectionName)

  /**
    *
    * @param finder          The finder to find an existing record.
    * @param modifierBson    The modifier to be applied
    * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
    *                        a BSONObjectId in order to understand if the update is an upsert or update.
    */
  @deprecated("Please use findAndUpdate method from ReactiveRepository instead", "7.0.0")
  def atomicUpsert(finder: BSONDocument, modifierBson: BSONDocument, idAttributeName: String = "_id")(
    implicit ec: ExecutionContext,
    reads: Reads[T]): Future[DatabaseUpdate[T]] =
    atomicSaveOrUpdate(finder, modifierBson, upsert = true, idAttributeName)
      .map { _.getOrElse(throw new EntityNotFoundException("Failed to receive updated object!")) }

  /**
    *
    * @param finder          The finder to find an existing record.
    * @param modifierBson    The modifier to be applied
    * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
    *                        a BSONObjectId in order to understand if the update is an upsert or update.
    */
  @deprecated("Please use findAndUpdate method from ReactiveRepository instead", "7.0.0")
  def atomicUpdate(finder: BSONDocument, modifierBson: BSONDocument, idAttributeName: String = "_id")(
    implicit ec: ExecutionContext,
    reads: Reads[T]): Future[Option[DatabaseUpdate[T]]] =
    atomicSaveOrUpdate(finder, modifierBson, upsert = false, idAttributeName)

  /**
    *
    * @param finder          The finder to find an existing record.
    * @param modifierBson    The modifier to be applied
    * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
    *                        a BSONObjectId in order to understand if the update is an upsert or update.
    */
  @deprecated("use atomicUpsert or atomicUpdate instead", "4.3.0")
  def atomicSaveOrUpdate(
    finder: BSONDocument,
    modifierBson: BSONDocument,
    upsert: Boolean,
    idAttributeName: String = "_id")(
    implicit ec: ExecutionContext,
    reads: Reads[T]): Future[Option[DatabaseUpdate[T]]] = withCurrentTime { implicit time =>
    val (updateCommand, insertDocumentId) = if (upsert) {
      val insertedId = BSONObjectID.generate
      (modifierBson ++ createIdOnInsertOnly(insertedId, idAttributeName), Some(insertedId))
    } else (modifierBson, None)

    for {
      updateResult <- bsonCollection.findAndModify(
                       selector = finder,
                       modifier = Update(updateCommand, fetchNewObject = true, upsert)
                     )
      saveOrUpdateResult <- updateResult.value match {
                             case Some(update) => toDbUpdate(update, insertDocumentId).map(Some(_))
                             case None         => Future.successful(None)
                           }
    } yield saveOrUpdateResult
  }

  private def le(updatedExisting: Boolean) =
    LastError(
      ok                = true,
      errmsg            = None,
      code              = None,
      lastOp            = None,
      n                 = 1,
      singleShard       = None,
      updatedExisting   = updatedExisting,
      upserted          = None,
      wnote             = None,
      wtimeout          = false,
      waited            = None,
      wtime             = None,
      writeErrors       = Nil,
      writeConcernError = None
    )

  private def toDbUpdate(s: BSONDocument, insertedId: Option[BSONObjectID])(
    implicit reads: Reads[T]): Future[DatabaseUpdate[T]] = {

    def createResult(result: T) = insertedId match {
      case Some(id) if isInsertion(id, result) =>
        DatabaseUpdate(le(updatedExisting = false), Saved[T](result))
      case _ =>
        DatabaseUpdate(le(updatedExisting = true), Updated[T](result, result))
    }

    JsObjectReader.read(s).validate[T] match {
      case JsSuccess(result, _) => Future.successful(createResult(result))
      case JsError(errors)      => Future.failed(JsResultException(errors))
    }
  }

  private def createIdOnInsertOnly(id: BSONObjectID, idAttributeName: String) =
    setOnInsert(BSONDocument(idAttributeName -> id))
}

class EntityNotFoundException(msg: String) extends Exception(msg)
