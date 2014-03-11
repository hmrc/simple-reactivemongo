/*
 * Copyright 2014 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.hmrc.mongo

import reactivemongo.core.commands.{LastError, Count}
import reactivemongo.api.DB
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.json.{Format, Json}
import org.joda.time.{DateTimeZone, DateTime}
import reactivemongo.json.collection.JSONCollection

trait Indexes {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val ec = global

  def ensureIndexes()
}

sealed abstract class UpdateType[A] {
  def savedValue: A
}

case class Saved[A](savedValue: A) extends UpdateType[A]

case class Updated[A](previousValue: A, savedValue: A) extends UpdateType[A]

case class DatabaseUpdate[A](writeResult: LastError, updateType: UpdateType[A])

trait Repository[A <: Any, ID <: Any] {

  def findAll(implicit ec: ExecutionContext): Future[List[A]] = ???

  def findById(id: ID)(implicit ec: ExecutionContext): Future[Option[A]] = ???

  def count(implicit ec: ExecutionContext): Future[Int] = ???

  def removeAll(implicit ec: ExecutionContext): Future[LastError] = ???

  def drop(implicit ec: ExecutionContext): Future[Boolean] = ???

  def save(entity: A)(implicit ec: ExecutionContext): Future[LastError] = ???

  def insert(entity: A)(implicit ec: ExecutionContext): Future[LastError] = ???

  def withCurrentTime[A](f: (DateTime) => A) = f(DateTime.now.withZone(DateTimeZone.UTC))

  def saveOrUpdate(findQuery: => Future[Option[A]], ifNotFound: => Future[A], modifiers: (A) => A)(implicit ec: ExecutionContext): Future[DatabaseUpdate[A]] = ???

}

abstract class ReactiveRepository[A <: Any, ID <: Any](collectionName: String, mongo: () => DB, domainFormat: Format[A], idFormat: Format[ID], mc: Option[JSONCollection] = None)(implicit manifest: Manifest[A], mid: Manifest[ID])
  extends Repository[A, ID] with Indexes {

  implicit val domainFormatImplicit = domainFormat
  implicit val idFormatImplicit = idFormat


  import reactivemongo.core.commands.GetLastError

  lazy val collection = mc.getOrElse(mongo().collection[JSONCollection](collectionName))

  ensureIndexes()

  override def findAll(implicit ec: ExecutionContext): Future[List[A]] = collection.find(Json.obj()).cursor[A].collect[List]()

  override def findById(id: ID)(implicit ec: ExecutionContext): Future[Option[A]] = {
    collection.find(Json.obj("_id" -> id)).one[A]
  }

  override def count(implicit ec: ExecutionContext): Future[Int] = mongo().command(Count(collection.name))

  override def removeAll(implicit ec: ExecutionContext): Future[LastError] = collection.remove(Json.obj(), GetLastError(), false)

  override def drop(implicit ec: ExecutionContext): Future[Boolean] = collection.drop.recover {
    case _ => false
  }

  override def save(entity: A)(implicit ec: ExecutionContext) = collection.save(entity)

  override def insert(entity: A)(implicit ec: ExecutionContext) = collection.insert(entity)

  override def saveOrUpdate(findQuery: => Future[Option[A]], ifNotFound: => Future[A], modifiers: (A) => A = a => a)(implicit ec: ExecutionContext): Future[DatabaseUpdate[A]] = {
    withCurrentTime {
      implicit time =>
        val updateTypeF = findQuery.flatMap {
          case Some(existingValue) => Future.successful(Updated(existingValue, modifiers(existingValue)))
          case None => ifNotFound.map(newValue => Saved(modifiers(newValue))): Future[UpdateType[A]]
        }

        updateTypeF.flatMap {
          updateType =>
            save(updateType.savedValue).map {
              lastErr =>
                DatabaseUpdate(writeResult = lastErr, updateType)
            }
        }
    }
  }
}