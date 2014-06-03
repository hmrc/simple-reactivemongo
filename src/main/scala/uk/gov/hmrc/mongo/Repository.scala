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

  lazy implicit val ec = global

  def ensureIndexes(): Future[_] = Future.successful(Unit)
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

  def findAll(implicit ec: ExecutionContext): Future[List[A]] = ???

  def findById(id: ID)(implicit ec: ExecutionContext): Future[Option[A]] = ???

  def find(query: (scala.Predef.String, play.api.libs.json.Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = ???

  def count(implicit ec: ExecutionContext): Future[Int] = ???

  def removeAll(implicit ec: ExecutionContext): Future[LastError] = ???

  def removeById(id: ID)(implicit ec: ExecutionContext): Future[LastError] = ???

  def remove(query: (scala.Predef.String, play.api.libs.json.Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[LastError] = ???

  def drop(implicit ec: ExecutionContext): Future[Boolean] = ???

  def save(entity: A)(implicit ec: ExecutionContext): Future[LastError] = ???

  def insert(entity: A)(implicit ec: ExecutionContext): Future[LastError] = ???

}
