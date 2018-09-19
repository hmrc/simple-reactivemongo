/*
 * Copyright 2018 HM Revenue & Customs
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

import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.LastError

import scala.concurrent.duration._
import reactivemongo.api.FailoverStrategy

trait MongoSpecSupport {

  protected def databaseName = "test-" + this.getClass.getSimpleName

  protected def mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName"

  implicit val mongoConnectorForTest = new MongoConnector(mongoUri)

  implicit val mongo = mongoConnectorForTest.db

  def bsonCollection(name: String)(
    failoverStrategy: FailoverStrategy = mongoConnectorForTest.helper.db.failoverStrategy): BSONCollection = {
    import reactivemongo.api._
    mongoConnectorForTest.helper.db(name, failoverStrategy)
  }

  def lastError(successful: Boolean, updated: Boolean = false, originalDoc: Option[BSONDocument] = None) =
    LastError(
      ok               = successful,
      err              = None,
      code             = None,
      errMsg           = None,
      originalDocument = originalDoc,
      updated          = if (updated) 1 else 0,
      updatedExisting  = updated)

}

trait Awaiting {

  import scala.concurrent._

  implicit val ec = ExecutionContext.Implicits.global

  val timeout = 5 seconds

  def await[A](future: Future[A])(implicit timeout: Duration = timeout) = Await.result(future, timeout)
}
