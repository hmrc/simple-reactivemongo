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


import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterEach, LoneElement}
import play.api.libs.json.Format
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType._
import reactivemongo.bson.BSONObjectID

class EnsureIndexDeleteSpec
  extends WordSpecLike
  with Matchers
  with Awaiting
  with MongoSpecSupport
  with ScalaFutures
  with IntegrationPatience
  with BeforeAndAfterEach {

  "Trying to delete an index" should {
    "work if the index already exists" in {
      repo.collection.indexesManager.create(Index(Seq("testField_1" -> Ascending), name = Some("indexToBeDeleted"))).futureValue
      repo.collection.indexesManager.create(Index(Seq("testField_2" -> Ascending), name = Some("indexToBeKept"))).futureValue

      repo.collection.db.command(EnsureIndexDelete(collectionName, "indexToBeDeleted")).futureValue

      repo.collection.indexesManager.list().futureValue.map(_.name) should be(List(Some("_id_"), Some("indexToBeKept")))
    }

    "do nothing if the index does not exist" in {
      repo.collection.indexesManager.create(Index(Seq("testField_2" -> Ascending), name = Some("indexToBeKept"))).futureValue

      repo.collection.db.command(EnsureIndexDelete(collectionName, "indexThatDoesntExist")).futureValue

      repo.collection.indexesManager.list().futureValue.map(_.name) should be(List(Some("_id_"), Some("indexToBeKept")))
    }
  }

  override protected def beforeEach() {
    await(repo.drop)
  }

  val collectionName = "EnsureIndexDeletedSpec"
  lazy val repo = new ReactiveRepository[String, BSONObjectID](collectionName = collectionName, mongo, implicitly[Format[String]]) {}
}
