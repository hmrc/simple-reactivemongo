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

  object Update extends Enumeration {
    type IndexUpdateType = Value
    val NO_UPDATE, INSERT, MODIFY = Value
  }

  final val ensureIndexFailedMessage: String = "ensuring index failed"

  protected def ensureIndex(index: Index)(implicit collection: JSONCollection, ec: ExecutionContext): Future[Boolean] = {
    collection.indexesManager.ensure(index).recover {
      case t =>
        logger.error(ensureIndexFailedMessage, t)
        false
    }
  }

  import Update._


  private def keyNeedsUpdate(newIndex: Index, existingIndex: Index) = {
    val newIndexKeys = newIndex.key.toMap
    val existingIndexKeys = existingIndex.key.toMap
    newIndexKeys.exists { case (name, _type) =>
      val existing = existingIndexKeys.get(name)
      !existing.isDefined || existing.get != _type
    }
  }

  private def flagsNeedsUpdate(index: Index, existingIndex: Index): Boolean = {
    Seq(index.background ^ existingIndex.background,
      index.dropDups ^ existingIndex.dropDups,
      index.sparse ^ existingIndex.sparse,
      index.unique ^ existingIndex.unique) exists(_ == true)
  }

  private def needsUpdate(newIndex: Index, existingIndex: Option[Index]): IndexUpdateType = existingIndex map { idx: Index =>
    if (newIndex.eventualName == idx.eventualName &&
      (newIndex.options  != idx.options || keyNeedsUpdate(newIndex, idx) || flagsNeedsUpdate(newIndex, idx))) MODIFY
    else NO_UPDATE
  } getOrElse (INSERT)

  def updateIndexDefinition(indexes: Index*)(implicit collection: JSONCollection): Future[Seq[Boolean]] = {
    collection.indexesManager.list() flatMap { existingIndexes =>
      val existingIndexesAsMap = existingIndexes.map(idx => (idx.eventualName, idx)).toMap
      Future.sequence {
        indexes map { newIndex =>
          val updateType = needsUpdate(newIndex, existingIndexesAsMap.get(newIndex.eventualName))
          if (NO_UPDATE != updateType) update(newIndex, updateType, existingIndexesAsMap.get(newIndex.eventualName))
          else Future.successful(false)
        }
      }
    }
  }

  private def update(newIndex: Index, updateType: IndexUpdateType, oldIndex: Option[Index])(implicit collection: JSONCollection): Future[Boolean] = {
    if (updateType == MODIFY && oldIndex.isDefined) for {
      deleted <- collection.db.command(EnsureIndexDelete(collection.name, newIndex.eventualName))
      updated <- collection.indexesManager.ensure(newIndex) recoverWith {
        case e =>
          logger.error(s"Definition of index ${newIndex.eventualName} cannot be updated because of: $e. Previous definition of this index will now be restored")
          ensureIndex(oldIndex.get) map (_ => false)
      }
    } yield updated
    else ensureIndex(newIndex)
  }

}