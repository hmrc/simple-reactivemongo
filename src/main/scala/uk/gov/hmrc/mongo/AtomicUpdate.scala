package uk.gov.hmrc.mongo

import play.api.libs.json.{Writes, Reads}
import reactivemongo.bson.{BSONObjectID, BSONDocument}
import reactivemongo.core.commands.{LastError, Update, FindAndModify}
import reactivemongo.json.ImplicitBSONHandlers.JsObjectReader
import reactivemongo.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

trait AtomicUpdate[T] extends CurrentTime with BSONBuilderHelpers {

  final val DEFAULT_ID="_id"
  final val  ATOMIC_ID="atomicId"

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
   * @param writes
   * @return
   */
  def atomicUpsert(finder: BSONDocument, modifierBson: BSONDocument, idAttributeName:String = "_id")
                  (implicit ec: ExecutionContext, reads: Reads[T], writes: Writes[T])
                   : Future[DatabaseUpdate[T]] =
    atomicSaveOrUpdate(finder, modifierBson, upsert = true, idAttributeName)
                      .map{_.getOrElse(
                               throw new EntityNotFoundException("Failed to receive updated object!"))}

  /**
   *
   * @param finder          The finder to find an existing record.
   * @param modifierBson    The modifier to be applied
   * @param upsert
   * @param idAttributeName Optional value to override the default object Id for the collection. Atomics MUST have a record Id to store
   *                        a BSONObjectId in order to understand if the update is an upsert or update.
   * @param ec
   * @param reads
   * @param writes
   * @return
   */
  def atomicSaveOrUpdate(finder: BSONDocument, modifierBson: BSONDocument, upsert: Boolean, idAttributeName:String = "_id")
                        (implicit ec: ExecutionContext, reads: Reads[T], writes: Writes[T])
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

    def createResult(result: T) = {
      if (insertedId.isDefined &&
          isInsertion(insertedId.getOrElse(throw new Exception("Failure!")), result))
        DatabaseUpdate(le(updatedExisting = false), Saved[T](result))
      else
        DatabaseUpdate(le(updatedExisting = true), Updated[T](result, result))
    }

    JsObjectReader.read(s).asOpt[T] match {
      case Some(result) => Future.successful(createResult(result))
      case None => Future.failed(new Exception("Failed to deserialize returned object into type!"))
    }
  }

  private def createIdOnInsertOnly(id:BSONObjectID, idAttributeName:String) = setOnInsert(BSONDocument(idAttributeName -> id))
}

class EntityNotFoundException(msg: String) extends Exception(msg)
