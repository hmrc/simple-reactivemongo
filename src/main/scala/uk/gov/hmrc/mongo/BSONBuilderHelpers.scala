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

import reactivemongo.bson.{BSONArray, BSONDocument}

trait BSONBuilderHelpers {

  def or(search: BSONDocument*): BSONDocument = BSONDocument("$or" -> BSONArray(search))

  def and(search: BSONDocument*): BSONDocument = BSONDocument("$and" -> BSONArray(search))

  def set(value: BSONDocument): BSONDocument = BSONDocument("$set" -> value)

  def setOnInsert(value: BSONDocument): BSONDocument = BSONDocument("$setOnInsert" -> value)

  def addToSet(value: BSONDocument): BSONDocument = BSONDocument("$addToSet" -> value)

}
