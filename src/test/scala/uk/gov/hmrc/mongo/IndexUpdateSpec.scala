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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{LoneElement, Matchers, WordSpec}
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection

class IndexUpdateSpec extends WordSpec with Matchers  with MongoSpecSupport with Awaiting with ScalaFutures with LoneElement {


  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(300, Millis)), interval = scaled(Span(150, Millis)))

  private trait Setup {
    await(mongo().drop())
    val testCollection = mongo().collection[JSONCollection]("testCollection")

    val indexUpdate = new IndexUpdate {
      override protected val logger = LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]
      override def collection: JSONCollection = testCollection
    }
  }

  "updating the type of index" should {

    "change the type of index and return true" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1))
      await(testCollection.indexesManager.create(index))

      val updatedIndex = index.copy(Seq("repoField" -> IndexType.Descending), unique = true)
      indexUpdate.updateIndexDefinition(updatedIndex).futureValue.loneElement shouldBe true

      testCollection.indexesManager.list().futureValue should contain(updatedIndex)
    }

    "do nothing and return false if index is up to date" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1))
      await(testCollection.indexesManager.create(index))


      indexUpdate.updateIndexDefinition(index).futureValue.loneElement shouldBe false

      testCollection.indexesManager.list().futureValue  should contain(index)
    }

    "insert a new index and return true if index does not exist already" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1))

      indexUpdate.updateIndexDefinition(index).futureValue.loneElement shouldBe true

      testCollection.indexesManager.list().futureValue  should contain(index)
    }

    "update one index out of two and return proper Seq[Boolean] when the index not being updated exists and is up to date" in new Setup {
      val index1 = Index(Seq("repoField1" -> IndexType.Ascending), name = Some("indexName1"), unique = false, sparse = false, version = Some(1))
      val index2 = Index(Seq("repoField2" -> IndexType.Ascending), name = Some("indexName2"), unique = true, sparse = false, version = Some(1))
      await(testCollection.indexesManager.create(index1))
      await(testCollection.indexesManager.create(index2))

      val updatedIndex = index1.copy(Seq("repoField1" -> IndexType.Descending))
      indexUpdate.updateIndexDefinition(updatedIndex, index2).futureValue shouldBe Seq(true, false)

      testCollection.indexesManager.list().futureValue  should (contain(index2) and contain(updatedIndex))
    }

    "update one index, insert the other and return proper Seq[Boolean] when the index being inserted does not exist already" in new Setup {
      val index1 = Index(Seq("repoField1" -> IndexType.Ascending), name = Some("indexName1"), unique = false, sparse = false, version = Some(1))
      val index2 = Index(Seq("repoField2" -> IndexType.Ascending), name = Some("indexName2"), unique = true, sparse = false, version = Some(1))
      await(testCollection.indexesManager.create(index1))


      val updatedIndex = index1.copy(Seq("repoField1" -> IndexType.Descending))
      indexUpdate.updateIndexDefinition(updatedIndex, index2).futureValue shouldBe Seq(true, true)

      testCollection.indexesManager.list().futureValue  should (contain(index2) and contain(updatedIndex))
    }

  }

  "updating the options of index" should {

    "change the options of index and return true if the given option is not set" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1))
      await(testCollection.indexesManager.create(index))

      val updatedIndex = index.copy(Seq("repoField" -> IndexType.Descending), options = BSONDocument("expireAfterSeconds" -> 123456L))
      indexUpdate.updateIndexDefinition(updatedIndex).futureValue.loneElement shouldBe true

      testCollection.indexesManager.list().futureValue should contain(updatedIndex)
    }

    "change the options of index and return true if the given option is set to a different value in the new index" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1), options = BSONDocument("expireAfterSeconds" -> 123456L))
      await(testCollection.indexesManager.create(index))

      val updatedIndex = index.copy(Seq("repoField" -> IndexType.Descending), options = BSONDocument("expireAfterSeconds" -> 123469L))
      indexUpdate.updateIndexDefinition(updatedIndex).futureValue.loneElement shouldBe true

      testCollection.indexesManager.list().futureValue should contain(updatedIndex)
    }

    "change the options of index and return true if the given option is not set in the new index" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1), options = BSONDocument("expireAfterSeconds" -> 123456L, "otherOption" -> "someValue"))
      await(testCollection.indexesManager.create(index))

      val updatedIndex = index.copy(Seq("repoField" -> IndexType.Descending), options = BSONDocument("expireAfterSeconds" -> 123456L))
      indexUpdate.updateIndexDefinition(updatedIndex).futureValue.loneElement shouldBe true

      testCollection.indexesManager.list().futureValue should contain(updatedIndex)
    }


    "remove the options of index and return true if the new index has no options" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1), options = BSONDocument("expireAfterSeconds" -> 123456L, "otherOption" -> "someValue"))
      await(testCollection.indexesManager.create(index))

      val updatedIndex = index.copy(Seq("repoField" -> IndexType.Descending))
      indexUpdate.updateIndexDefinition(updatedIndex).futureValue.loneElement shouldBe true

      testCollection.indexesManager.list().futureValue should contain(updatedIndex)
    }

    "do nothing and return false if index is up to date" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1), options = BSONDocument("expireAfterSeconds" -> 123456L))
      await(testCollection.indexesManager.create(index))


      indexUpdate.updateIndexDefinition(index).futureValue.loneElement shouldBe false

      testCollection.indexesManager.list().futureValue should contain(index)
    }
  }

  "updating index when records that don\'t comply with the index exist" should {
    "do not update index and log error" in new Setup {
      val index = Index(Seq("repoField" -> IndexType.Ascending), name = Some("indexName"), unique = false, sparse = false, version = Some(1))
      await(testCollection.indexesManager.create(index))

      testCollection.save(Json.obj("repoField" -> "duplicate" )).futureValue should have('inError(false))
//      testCollection.save(Json.obj("repoField" -> "duplicate" )).futureValue should have('inError(false))

      val updatedIndex = index.copy(unique = true)

      indexUpdate.updateIndexDefinition(updatedIndex).futureValue.loneElement shouldBe false
      testCollection.indexesManager.list().futureValue should contain(index)
    }
  }
}
