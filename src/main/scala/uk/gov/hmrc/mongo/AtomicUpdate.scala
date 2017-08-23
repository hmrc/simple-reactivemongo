package uk.gov.hmrc.mongo

import play.api.libs.json.{Writes, Reads, JsSuccess, JsError, JsResultException}
import reactivemongo.bson.{BSONObjectID, BSONDocument}
import reactivemongo.core.commands.{LastError, Update, FindAndModify}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectReader
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

trait AtomicUpdate[T] extends CurrentTime with BSONBuilderHelpers {

  def isInsertion(newRecordId: BSONObjectID, oldRecord: T): Boolean

  def collection: JSONCollection

  /**
   *
   * @param finder          The finder to find an existing record.
   * @param modifierBson    The modifier to be applied
   * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
   *                        a BSONObjectId in order to understand if the update is an upsert or update.
   * @param ec
   * @param reads
   * @return
   */
  def atomicUpsert(finder: BSONDocument, modifierBson: BSONDocument, idAttributeName:String = "_id")
                  (implicit ec: ExecutionContext, reads: Reads[T])
                   : Future[DatabaseUpdate[T]] =
    atomicSaveOrUpdate(finder, modifierBson, upsert = true, idAttributeName)
                      .map{_.getOrElse(
                               throw new EntityNotFoundException("Failed to receive updated object!"))}
  
  /**
   *
   * @param finder          The finder to find an existing record.
   * @param modifierBson    The modifier to be applied
   * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
   *                        a BSONObjectId in order to understand if the update is an upsert or update.
   * @param ec
   * @param reads
   * @return
   */
  def atomicUpdate(finder: BSONDocument, modifierBson: BSONDocument, idAttributeName:String = "_id")
                  (implicit ec: ExecutionContext, reads: Reads[T])
                   : Future[Option[DatabaseUpdate[T]]] =
    atomicSaveOrUpdate(finder, modifierBson, upsert = false, idAttributeName)

  /**
   *
   * @param finder          The finder to find an existing record.
   * @param modifierBson    The modifier to be applied
   * @param upsert
   * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
   *                        a BSONObjectId in order to understand if the update is an upsert or update.
   * @param ec
   * @param reads
   * @return
   */
  @deprecated("use atomicUpsert or atomicUpdate instead", "4.3.0")
  def atomicSaveOrUpdate(finder: BSONDocument, modifierBson: BSONDocument, upsert: Boolean, idAttributeName:String = "_id")
                        (implicit ec: ExecutionContext, reads: Reads[T])
                        : Future[Option[DatabaseUpdate[T]]] = withCurrentTime {
      implicit time =>

        val (updateCommand, insertDocumentId) = if (upsert) {
          val insertedId = BSONObjectID.generate
          (modifierBson ++ createIdOnInsertOnly(insertedId, idAttributeName), Some(insertedId))
        } else (modifierBson, None)

        val command = FindAndModify(collection.name,
                                    finder,
                                    Update(updateCommand,
                                    fetchNewObject = true),
                                    upsert = upsert,
                                    sort = None,
                                    fields = None)

        for {
          maybeUpdated <- collection.db.command(command)

          saveOrUpdateResult <- maybeUpdated match {
            case Some(update) => toDbUpdate(update, insertDocumentId).map(Some(_))
            case None => Future.successful(None)
          }
        } yield saveOrUpdateResult
    }

  private def le(updatedExisting:Boolean) = new LastError(ok = true,
                                                          err = None,
                                                          code = None,
                                                          errMsg = None,
                                                          originalDocument = None,
                                                          updated = 1,
                                                          updatedExisting = updatedExisting)

  private def toDbUpdate(s: BSONDocument, insertedId: Option[BSONObjectID])
                        (implicit reads: Reads[T]): Future[DatabaseUpdate[T]] = {

    def createResult(result: T) = insertedId match {
      case Some(insertedId) if isInsertion(insertedId, result) =>
        DatabaseUpdate(le(updatedExisting = false), Saved[T](result))
      case _ =>
        DatabaseUpdate(le(updatedExisting = true), Updated[T](result, result))
    }

    JsObjectReader.read(s).validate[T] match {
      case JsSuccess(result, _) => Future.successful(createResult(result))
      case JsError(errors) => Future.failed(new JsResultException(errors))
    }
  }

  private def createIdOnInsertOnly(id:BSONObjectID, idAttributeName:String) = setOnInsert(BSONDocument(idAttributeName -> id))
}

class EntityNotFoundException(msg: String) extends Exception(msg)
