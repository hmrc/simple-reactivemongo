package uk.gov.hmrc.mongo

import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.core.commands.Count
import reactivemongo.core.errors.{GenericDatabaseException, DetailedDatabaseException, DatabaseException}
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}


abstract class ReactiveRepository[A <: Any, ID <: Any](collectionName: String,
                                                       mongo: () => DB,
                                                       domainFormat: Format[A],
                                                       idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats,
                                                       mc: Option[JSONCollection] = None)
                                                      (implicit manifest: Manifest[A], mid: Manifest[ID], ec : ExecutionContext)
  extends Repository[A, ID] with Indexes with ImplicitBSONHandlers {

  import play.api.libs.json.Json.JsValueWrapper

  implicit val domainFormatImplicit = domainFormat
  implicit val idFormatImplicit = idFormat

  lazy val collection = mc.getOrElse(mongo().collection[JSONCollection](collectionName))

  protected val logger = LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]
  val message: String = "Failed to ensure index"

  ensureIndexes

  protected val _Id = "_id"
  protected def _id(id : ID) = Json.obj(_Id -> id)

  override def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = {
    collection.find(Json.obj(query: _*)).cursor[A](ReadPreference.primaryPreferred).collect[List]() //TODO: pass in ReadPreference
  }

  override def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred, limit: Option[Int] = None, afterId: Option[ID] = None)
                      (implicit ec: ExecutionContext): Future[List[A]] = {
    val query = afterId.map(id => Json.obj("_id" -> Json.obj("$gt" -> id))).getOrElse(Json.obj())
    val size = limit.getOrElse(Int.MaxValue)

    collection.find(query).sort(Json.obj("_id" -> 1)).cursor[A](readPreference).collect[List](size)
  }

  override def findById(id: ID, readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[Option[A]] = {
    collection.find(_id(id)).one[A](readPreference)
  }

  override def count(implicit ec: ExecutionContext): Future[Int] = mongo().command(Count(collection.name))

  override def removeAll(writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj(), writeConcern)
  }

  override def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext) = {
    collection.remove(_id(id), writeConcern)
  }

  override def remove(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj(query: _*), WriteConcern.Default) //TODO: pass in the WriteConcern
  }

  override def drop(implicit ec: ExecutionContext) = collection.drop.map(_ => true).recover[Boolean] {
    case _ => false
  }

  @deprecated("use ReactiveRepository#insert() instead", "3.0.1")
  override def save(entity: A)(implicit ec: ExecutionContext) = insert(entity)

  override def insert(entity: A)(implicit ec: ExecutionContext) = {
    domainFormat.writes(entity) match {
        case d @ JsObject(_) => collection.insert(d)
        case _ =>
          Future.failed[WriteResult](new Exception("cannot write object"))
      }
  }


  private val DuplicateKeyError = "E11000"
  private def ensureIndex(index: Index)(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.indexesManager.create(index).map(wr => {
     if(!wr.ok) {
       val maybeMsg = for {
         msg <- wr.errmsg
         m <- if (msg.contains(DuplicateKeyError)) {
           // this is for backwards compatibility to mongodb 2.6.x
           throw new GenericDatabaseException(msg, wr.code)
         }else Some(msg)
       } yield m
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
