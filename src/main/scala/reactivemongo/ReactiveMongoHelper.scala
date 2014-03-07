/*
 * Copyright 2012-2013 Stephane Godbillon (@sgodbillon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactivemongo

import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.ExecutionContext
import reactivemongo.api.{FailoverStrategy, DB, MongoDriver}

case class ReactiveMongoHelper(dbName: String,
                               servers: List[String],
                               auth: List[Authenticate],
                               nbChannelsPerNode: Option[Int],
                               failoverStrategy: Option[FailoverStrategy]) {
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  lazy val driver = new MongoDriver
  lazy val connection = nbChannelsPerNode match {
    case Some(numberOfChannels) => driver.connection(servers, auth, nbChannelsPerNode = numberOfChannels)
    case _                      => driver.connection(servers, auth)
  }
  lazy val db = failoverStrategy match {
    case Some(fs : FailoverStrategy) => DB(dbName, connection, fs)
    case None => DB(dbName, connection)
  }
}

