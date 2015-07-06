package uk.gov.hmrc.mongo

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import reactivemongo.api.indexes.Index
import play.api.libs.json.{JsNumber, JsObject, Format, Json}
import reactivemongo.api.{DB, QueryOpts}
import reactivemongo.api.SortOrder
import reactivemongo.core.commands.{Count, LastError}
import reactivemongo.json.collection.JSONCollection
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


abstract class ReactiveRepository[A <: Any, ID <: Any](collectionName: String,
                                                       mongo: () => DB,
                                                       domainFormat: Format[A],
                                                       idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats,
                                                       mc: Option[JSONCollection] = None)
                                                      (implicit manifest: Manifest[A], mid: Manifest[ID])
  extends Repository[A, ID] with Indexes {

  import play.api.libs.json.Json.JsValueWrapper
  import scala.concurrent.ExecutionContext.Implicits.global
  import reactivemongo.core.commands.GetLastError

  implicit val domainFormatImplicit = domainFormat
  implicit val idFormatImplicit = idFormat

  lazy val collection = mc.getOrElse(mongo().collection[JSONCollection](collectionName))

  protected val logger = LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]
  val message: String = "ensuring index failed"

  ensureIndexes

  override def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = {
    collection.find(Json.obj(query: _*)).cursor[A].collect[List]()
  }

  def findAllPaged(pageSize: Int, page: Int)(implicit ec: ExecutionContext): Future[List[A]] ={
    val amountToSkip = (page - 1) * pageSize
    collection.find(Json.obj())
              .sort(JsObject(Seq(("_id", JsNumber(1)))))
              .options(QueryOpts(amountToSkip, pageSize))
              .cursor[A]
              .collect[List](pageSize)
}
  override def findAll(implicit ec: ExecutionContext): Future[List[A]] = collection.find(Json.obj()).cursor[A].collect[List]()

  override def findById(id: ID)(implicit ec: ExecutionContext): Future[Option[A]] = {
    collection.find(Json.obj("_id" -> id)).one[A]
  }

  override def count(implicit ec: ExecutionContext): Future[Int] = mongo().command(Count(collection.name))

  override def removeAll(implicit ec: ExecutionContext): Future[LastError] = collection.remove(Json.obj(), GetLastError(), false)

  override def removeById(id: ID)(implicit ec: ExecutionContext): Future[LastError] = collection.remove(Json.obj("_id" -> id), GetLastError(), false)

  override def remove(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[LastError] = {
    collection.remove(Json.obj(query: _*), GetLastError(), false)
  }

  override def drop(implicit ec: ExecutionContext): Future[Boolean] = collection.drop.recover {
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
