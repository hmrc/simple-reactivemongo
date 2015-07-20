package uk.gov.hmrc.mongo

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import play.api.libs.json.{Format, Json}
import reactivemongo.api.{ReadPreference, DB}
import reactivemongo.core.commands.Count
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.json._, ImplicitBSONHandlers._

import scala.concurrent.{ExecutionContext, Future}


abstract class ReactiveRepository[A <: Any, ID <: Any](collectionName: String,
                                                       mongo: () => DB,
                                                       domainFormat: Format[A],
                                                       idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats,
                                                       mc: Option[JSONCollection] = None)
                                                      (implicit manifest: Manifest[A], mid: Manifest[ID], ec : ExecutionContext)
  extends Repository[A, ID] with Indexes {

  import play.api.libs.json.Json.JsValueWrapper

  implicit val domainFormatImplicit = domainFormat
  implicit val idFormatImplicit = idFormat

  lazy val collection = mc.getOrElse(mongo().collection[JSONCollection](collectionName))

  protected val logger = LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]
  val message: String = "ensuring index failed"

  ensureIndexes

  override def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = {
    collection.find(Json.obj(query: _*)).cursor[A](ReadPreference.secondaryPreferred).collect[List]()
  }

  override def findAll(implicit ec: ExecutionContext): Future[List[A]] = collection.find(Json.obj()).cursor[A].collect[List]()

  override def findById(id: ID)(implicit ec: ExecutionContext): Future[Option[A]] = {
    collection.find(Json.obj("_id" -> id)).one[A]
  }

  override def count(implicit ec: ExecutionContext): Future[Int] = mongo().command(Count(collection.name))

  override def removeAll(writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj(), writeConcern, false)
  }

  override def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj("_id" -> id), writeConcern, false)
  }

  override def remove(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext) = {
    collection.remove(Json.obj(query: _*), WriteConcern.Default, false) //TODO: pass in the WriteConcern
  }

  override def drop(implicit ec: ExecutionContext): Future[Boolean] = collection.drop.recover[Boolean] {
    case _ => false
  }

  override def save(entity: A)(implicit ec: ExecutionContext) = collection.save(entity)

  override def insert(entity: A)(implicit ec: ExecutionContext) = collection.insert(entity)

  private def ensureIndex(index: Index)(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.indexesManager.ensure(index).recover {
      case t =>
        logger.error(message, t)
        false
    }
  }

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(ensureIndex))
  }

}
