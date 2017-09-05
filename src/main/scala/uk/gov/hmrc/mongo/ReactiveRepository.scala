package uk.gov.hmrc.mongo

import org.slf4j.LoggerFactory
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.core.commands.Count
import reactivemongo.core.errors.GenericDatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

object ReactiveRepository {
  def enrichWriteResult(w: WriteResult): WriteResult with WriteResultErrorFlag = w match {
    case DefaultWriteResult(ok, n, writeErrors, writeConcernError, code, errmsg) =>
      new DefaultWriteResult(ok, n, writeErrors, writeConcernError, code, errmsg) with WriteResultErrorFlag
    case UpdateWriteResult(ok, n, nModified, upserted, writeErrors, writeConcernError, code, errmsg) =>
      new UpdateWriteResult(ok, n, nModified, upserted, writeErrors, writeConcernError, code, errmsg) with WriteResultErrorFlag
    case LastError(ok, errmsg, code, lastOp, n, singleShard, updatedExisting, upserted, wnote, wtimeout, waited, wtime, writeErrors, writeConcernError) =>
      new LastError(ok, errmsg, code, lastOp, n, singleShard, updatedExisting, upserted, wnote, wtimeout, waited, wtime, writeErrors, writeConcernError) with WriteResultErrorFlag
  }
}

abstract class ReactiveRepository[A <: Any, ID <: Any](collectionName: String,
                                                       mongo: () => DB,
                                                       domainFormat: Format[A],
                                                       idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats,
                                                       mc: Option[JSONCollection] = None)
                                                      (implicit manifest: Manifest[A], mid: Manifest[ID])
  extends Repository[A, ID] with Indexes {

  import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper
  import ReactiveRepository.enrichWriteResult

  implicit val domainFormatImplicit = domainFormat
  implicit val idFormatImplicit = idFormat

  lazy val collection = mc.getOrElse(mongo().collection[JSONCollection](collectionName))

  protected val logger = LoggerFactory.getLogger(this.getClass)
  val message: String = "Failed to ensure index"

  ensureIndexes(scala.concurrent.ExecutionContext.Implicits.global)

  protected val _Id = "_id"
  protected def _id(id : ID) = Json.obj(_Id -> id)

  override def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = {
    collection.find(Json.obj(query: _*)).cursor[A](ReadPreference.primaryPreferred).collect[List]() //TODO: pass in ReadPreference
  }

  override def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[A]] = {
    collection.find(Json.obj()).cursor[A](readPreference).collect[List]()
  }

  override def findById(id: ID, readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[Option[A]] = {
    collection.find(_id(id)).one[A](readPreference)
  }

  override def count(implicit ec: ExecutionContext): Future[Int] = mongo().command(Count(collection.name))

  override def removeAll(writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj(), writeConcern).map(enrichWriteResult)
  }

  override def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext) = {
    collection.remove(_id(id), writeConcern).map(enrichWriteResult)
  }

  override def remove(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj(query: _*), WriteConcern.Default).map(enrichWriteResult) //TODO: pass in the WriteConcern
  }

  override def drop(implicit ec: ExecutionContext) = collection.drop.map(_ => true).recover[Boolean] {
    case _ => false
  }

  @deprecated("use ReactiveRepository#insert() instead", "3.0.1")
  override def save(entity: A)(implicit ec: ExecutionContext) = insert(entity)

  override def insert(entity: A)(implicit ec: ExecutionContext): Future[WriteResult with WriteResultErrorFlag] = {
    domainFormat.writes(entity) match {
        case d @ JsObject(_) => collection.insert(d).map(enrichWriteResult)
        case _ =>
          Future.failed[WriteResult with WriteResultErrorFlag](new Exception("cannot write object"))
      }
  }

  override def bulkInsert(entities: Seq[A])(implicit ec: ExecutionContext): Future[MultiBulkWriteResult] = {
    val docs = entities.map(toJsObject)
    val failures = docs.collect { case Left(f) => f }
    lazy val successes = docs.collect { case Right(x) => x }
    if (failures.isEmpty)
      collection.bulkInsert(successes.toStream, false)
    else
      Future.failed[MultiBulkWriteResult](new BulkInsertRejected())
  }

  private def toJsObject(entity: A) = domainFormat.writes(entity) match {
    case j: JsObject => Right(j)
    case _ => Left(entity)
  }

  class BulkInsertRejected extends Exception("No objects inserted. Error converting some or all to JSON")

  private val DuplicateKeyError = "E11000"
  private def ensureIndex(index: Index)(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.indexesManager.create(index).map(wr => {
     if(!wr.ok) {
       val msg = wr.writeErrors.mkString(", ")
       val maybeMsg = if (msg.contains(DuplicateKeyError)) {
           // this is for backwards compatibility to mongodb 2.6.x
           throw new GenericDatabaseException(msg, wr.code)
         } else Some(msg)
       logger.error(s"$message : '${maybeMsg.map(_.toString)}'")
     }
     wr.ok
    }).recover {
      case t =>
        logger.error(message, t)
        false
    }
  }

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(ensureIndex))
  }

}
