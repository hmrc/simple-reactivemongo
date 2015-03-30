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

import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import reactivemongo.api.indexes.Index
import reactivemongo.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


trait IndexUpdate {

  protected def logger: Logger = LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]

  final val ensureIndexFailedMessage: String = "ensuring index failed"


  def updateIndexDefinition(indexes: Index*)
                           (implicit collection: JSONCollection): Future[Seq[Boolean]] = collection.indexesManager.list() flatMap { existingIndexes =>
    val existingIndexesAsMap = existingIndexes.map(idx => (idx.eventualName, idx)).toMap
    Future.sequence {
      indexes map { newIndex =>
        upsertOrIgnore(newIndex, existingIndexesAsMap.get(newIndex.eventualName))
      }
    }
  }

  protected def ensureIndex(index: Index)(implicit collection: JSONCollection, ec: ExecutionContext): Future[Boolean] = {
    collection.indexesManager.ensure(index).recover {
      case t =>
        logger.error(ensureIndexFailedMessage, t)
        false
    }
  }

  private def upsertOrIgnore(newIndex: Index, oldIndex: Option[Index])
                            (implicit collection: JSONCollection): Future[Boolean] = oldIndex map { existingIndex =>
    if (requiresUpdate(newIndex, existingIndex)) update(newIndex, existingIndex) else Future.successful(false)
  } getOrElse (ensureIndex(newIndex))

  private def update(newIndex: Index, oldIndex: Index)
                    (implicit collection: JSONCollection): Future[Boolean] = for {
    deleted <- collection.db.command(EnsureIndexDelete(collection.name, newIndex.eventualName))
    updated <- collection.indexesManager.ensure(newIndex) recoverWith {
      case e =>
        logger.error(s"Definition of index ${newIndex.eventualName} cannot be updated because of: $e. Previous definition of this index will now be restored")
        ensureIndex(oldIndex) map (_ => false)
    }
  } yield updated

  private def keyNeedsUpdate(newIndex: Index, existingIndex: Index) = {
    val newIndexKeys = newIndex.key.toMap
    val existingIndexKeys = existingIndex.key.toMap
    newIndexKeys.exists { case (name, _type) =>
      val existing = existingIndexKeys.get(name)
      !existing.isDefined || existing.get != _type
    }
  }

  private def flagsNeedUpdate(index: Index, existingIndex: Index): Boolean =
    Seq(index.background ^ existingIndex.background,
        index.dropDups ^ existingIndex.dropDups,
        index.sparse ^ existingIndex.sparse,
        index.unique ^ existingIndex.unique) exists (_ == true)

  private def requiresUpdate(newIndex: Index, existingIndex: Index): Boolean = (newIndex.eventualName == existingIndex.eventualName &&
    (newIndex.options != existingIndex.options || keyNeedsUpdate(newIndex, existingIndex) || flagsNeedUpdate(newIndex, existingIndex)))

}