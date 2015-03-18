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

import reactivemongo.bson.{BSONInteger, BSONString, BSONDocument}
import reactivemongo.core.commands.{CommandError, BSONCommandResultMaker, Command}


case class EnsureIndexDelete(collection: String, index: String) extends Command[Int] {
  override def makeDocuments = BSONDocument(
    "deleteIndexes" -> BSONString(collection),
    "index" -> BSONString(index))

  object ResultMaker extends BSONCommandResultMaker[Int] {
    def apply(document: BSONDocument) = CommandError
      .checkOk(document, Some("deleteIndexes"))
      .filterNot(_.getMessage().contains(s"index not found with name [$index]"))
      .toLeft(document.getAs[BSONInteger]("nIndexesWas").map(_.value.toInt).get)
  }
}
