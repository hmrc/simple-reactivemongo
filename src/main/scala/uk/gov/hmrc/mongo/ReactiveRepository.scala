/*
 * Copyright 2015 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}
import reactivemongo.api.DB
import reactivemongo.core.commands.{Count, LastError}
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}


abstract class ReactiveRepository[A <: Any, ID <: Any](collectionName: String,
                                                       mongo: () => DB,
                                                       domainFormat: Format[A],
                                                       idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats,
                                                       mc: Option[JSONCollection] = None)
                                                      (implicit manifest: Manifest[A], mid: Manifest[ID])
  extends Repository[A, ID] with Indexes with IndexUpdate {

  import play.api.libs.json.Json.JsValueWrapper
  import reactivemongo.core.commands.GetLastError

import scala.concurrent.ExecutionContext.Implicits.global

  implicit val domainFormatImplicit = domainFormat
  implicit val idFormatImplicit = idFormat

  implicit lazy val collection = mc.getOrElse(mongo().collection[JSONCollection](collectionName))

  def updateExistingIndexes: Boolean

  ensureIndexes

  override def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[A]] = {
    collection.find(Json.obj(query: _*)).cursor[A].collect[List]()
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

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    if (updateExistingIndexes) updateIndexDefinition(indexes :_*)
    else Future.sequence(indexes.map(ensureIndex))

}
