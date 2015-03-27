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
import reactivemongo.api.indexes.Index
import reactivemongo.bson.{BSONDocument, BSONInteger, BSONString}
import reactivemongo.core.commands.{BSONCommandResultMaker, Command, CommandError}
import reactivemongo.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait IndexUpdate {
  def collection: JSONCollection

  protected def logger: Logger

  object Update extends Enumeration {
    type IndexUpdateType = Value
    val NO_UPDATE, INSERT, MODIFY = Value
  }

  import Update._

  private def needsUpdate(newIndex: Index, existingIndex: Option[Index]): IndexUpdateType = {
    def keyNeedsUpdate(newIndex: Index, existingIndex: Index) = {
      val newIndexKeys = newIndex.key.toMap
      val existingIndexKeys = existingIndex.key.toMap
      newIndexKeys.exists { case (name, _type) =>
        val existing = existingIndexKeys.get(name)
        !existing.isDefined || existing.get != _type
      }
    }

    existingIndex map { idx: Index =>
      if (newIndex.eventualName == idx.eventualName &&
        (!newIndex.options.equals(idx.options) || keyNeedsUpdate(newIndex, idx))) MODIFY
      else NO_UPDATE
    } getOrElse (INSERT)
  }

  def updateIndexDefinition(indexes: Index*): Future[Seq[Boolean]] = {
    collection.indexesManager.list() flatMap { existingIndexes =>
      val existingIndexesAsMap = existingIndexes.map(idx => (idx.eventualName, idx)).toMap
      Future.sequence {
        indexes map { newIndex =>
          val updateType = needsUpdate(newIndex, existingIndexesAsMap.get(newIndex.eventualName))
          if (NO_UPDATE != updateType) update(newIndex, updateType)
          else Future.successful(false)
        }
      }
    }
  }

  private def update(newIndex: Index, updateType: IndexUpdateType): Future[Boolean] = {
    val ensureIndex = () => collection.indexesManager.ensure(newIndex).recover {
      case t =>
        logger.error("ensuring index failed", t)
        false
    }
    if (updateType == MODIFY) for {
      deleted <- collection.db.command(DeleteIndex(collection.name, newIndex.eventualName))
      updated <- ensureIndex()
    } yield updated
    else ensureIndex()
  }

}

sealed case class DeleteIndex(
                               collection: String,
                               index: String) extends Command[Int] {
  override def makeDocuments = BSONDocument(
    "deleteIndexes" -> BSONString(collection),
    "index" -> BSONString(index))

  object ResultMaker extends BSONCommandResultMaker[Int] {
    def apply(document: BSONDocument) =
      CommandError.checkOk(document, Some("deleteIndexes")).toLeft(document.getAs[BSONInteger]("nIndexesWas").map(_.value).get)
  }

}
