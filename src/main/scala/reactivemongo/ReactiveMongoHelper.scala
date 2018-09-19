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

package reactivemongo

import reactivemongo.core.nodeset.Authenticate

import scala.concurrent.{Await, ExecutionContext, Future}
import reactivemongo.api._

import scala.language.postfixOps

object ReactiveMongoHelper {

  @deprecated(message = "use case class constructor that takes MongoConnectionOptions", "0.1.0")
  def apply(
    dbName: String,
    servers: Seq[String],
    auth: Seq[Authenticate],
    nbChannelsPerNode: Option[Int],
    failoverStrategy: Option[FailoverStrategy]): ReactiveMongoHelper = {
    val mongoOpts = nbChannelsPerNode
      .map { n =>
        MongoConnectionOptions().copy(nbChannelsPerNode = n)
      }
      .getOrElse(MongoConnectionOptions())
    this(dbName, servers, auth, failoverStrategy, mongoOpts)
  }
}

case class ReactiveMongoHelper(
  dbName: String,
  servers: Seq[String],
  auth: Seq[Authenticate],
  failoverStrategy: Option[FailoverStrategy],
  connectionOptions: MongoConnectionOptions = MongoConnectionOptions()) {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  lazy val driver                   = new MongoDriver

  lazy val connection: MongoConnection = driver.connection(
    servers,
    authentications = auth,
    options         = connectionOptions
  )

  import scala.concurrent.duration._
  lazy val db: DefaultDB = failoverStrategy match {
    case Some(fs: FailoverStrategy) => Await.result(connection.database(dbName, fs), 10 seconds)
    case None                       => Await.result(connection.database(dbName), 10 seconds)
  }
}
