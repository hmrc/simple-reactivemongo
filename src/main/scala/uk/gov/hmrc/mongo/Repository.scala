package uk.gov.hmrc.mongo

import org.joda.time.{DateTime, DateTimeZone}
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import reactivemongo.core.commands.LastError

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

  protected lazy val zone = DateTimeZone.UTC

  def withCurrentTime[A](f: (DateTime) => A) = f(DateTime.now.withZone(zone))
}


trait Repository[A <: Any, ID <: Any] extends CurrentTime {

  import reactivemongo.api.ReadPreference

  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[A]] = ???

  def findById(id: ID, readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[Option[A]] = ???

  def find(query: (scala.Predef.String, play.api.libs.json.Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = ???

  def count(implicit ec: ExecutionContext): Future[Int] = ???

  def removeAll(writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult] = ???

  def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult] = ???

  def remove(query: (scala.Predef.String, play.api.libs.json.Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[WriteResult] = ???

  def drop(implicit ec: ExecutionContext): Future[Boolean] = ???

  def save(entity: A)(implicit ec: ExecutionContext): Future[WriteResult] = ???

  def insert(entity: A)(implicit ec: ExecutionContext): Future[WriteResult] = ???

  def bulkInsert(entities: Seq[A])(implicit ec: ExecutionContext): Future[MultiBulkWriteResult]

}
