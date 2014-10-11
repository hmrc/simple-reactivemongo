/*
 * Copyright 2014 HM Revenue & Customs
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
package uk.gov.hmrc.mongo

import reactivemongo.api.FailoverStrategy

trait SimpleMongoConnection {

  import reactivemongo.api.{MongoConnection, DefaultDB}
  import scala.util.{Failure, Success}
  import scala.concurrent.Await
  import scala.concurrent.duration._
  import reactivemongo.ReactiveMongoHelper

  val mongoConnectionUri: String
  val failoverStrategy: Option[FailoverStrategy]

  implicit def db: () => DefaultDB = () => mongoDb

  private lazy val mongoDb = connect

  private def connect = helper.db

  val channelsPerNode: Option[Int]

  lazy val helper = MongoConnection.parseURI(mongoConnectionUri) match {
    case Success(MongoConnection.ParsedURI(hosts, options, ignoreOptions, Some(db), auth)) =>
      ReactiveMongoHelper(db, hosts.map(h => h._1 + ":" + h._2).toList, auth.toList, channelsPerNode, failoverStrategy)
    case Success(MongoConnection.ParsedURI(_, _, _, None, _)) =>
      throw new Exception(s"Missing database name in mongodb.uri '$mongoConnectionUri'")
    case Failure(e) => throw new Exception(s"Invalid mongodb.uri '$mongoConnectionUri'", e)
  }

  def close() {
    val f = helper.connection.askClose()(10 seconds)
    Await.ready(f, 10 seconds)
  }

}

case class MongoConnector(val mongoConnectionUri: String, channelsPerNode: Option[Int] = None, failoverStrategy : Option[FailoverStrategy] = None) extends SimpleMongoConnection