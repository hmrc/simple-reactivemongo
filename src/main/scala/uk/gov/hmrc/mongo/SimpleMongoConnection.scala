/*
 * Copyright 2020 HM Revenue & Customs
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

import reactivemongo.api.{FailoverStrategy, MongoConnectionOptions}

import scala.concurrent.duration.FiniteDuration

trait SimpleMongoConnection {

  import reactivemongo.api.{DefaultDB, MongoConnection}
  import scala.util.{Failure, Success}
  import scala.concurrent.Await
  import scala.concurrent.duration._
  import reactivemongo.ReactiveMongoHelper

  val mongoConnectionUri: String
  val failoverStrategy: Option[FailoverStrategy]
  val dbTimeout: Option[FiniteDuration]
  val defaultHeartbeatFrequencyMS: Option[Int]

  implicit def db: () => DefaultDB = () => mongoDb

  private lazy val mongoDb = connect

  private def connect = helper.db

  lazy val helper: ReactiveMongoHelper = MongoConnection.parseURI(mongoConnectionUri) match {
    case Success(MongoConnection.ParsedURI(hosts, options, ignoreOptions, Some(db), auth)) =>
      val modifiedOptions = overrideDefaultHeartbeatFrequencyMS(options)
      ReactiveMongoHelper(db, hosts.map(h => h._1 + ":" + h._2), auth.toList, failoverStrategy, modifiedOptions, dbTimeout)

    case Success(MongoConnection.ParsedURI(_, _, _, None, _)) =>
      throw new Exception(s"Missing database name in mongodb.uri '$mongoConnectionUri'")

    case Failure(e) => throw new Exception(s"Invalid mongodb.uri '$mongoConnectionUri'", e)
  }

  private def overrideDefaultHeartbeatFrequencyMS(options: MongoConnectionOptions) = {
    def uriContainsKey: Boolean = mongoConnectionUri.contains("heartbeatFrequencyMS") || mongoConnectionUri.contains("rm.monitorRefreshMS")

    defaultHeartbeatFrequencyMS match {
      case Some(freq) if !uriContainsKey =>
        options.copy(heartbeatFrequencyMS = freq)
      case _ =>
        options
    }
  }

  def close() {
    val f = helper.connection.askClose()(dbTimeout.getOrElse(ReactiveMongoHelper.DEFAULT_DB_TIMEOUT))
    Await.ready(f, dbTimeout.getOrElse(ReactiveMongoHelper.DEFAULT_DB_TIMEOUT))
  }

}

case class MongoConnector(mongoConnectionUri: String,
                          failoverStrategy: Option[FailoverStrategy] = None,
                          dbTimeout: Option[FiniteDuration] = None,
                          defaultHeartbeatFrequencyMS: Option[Int] = None)
    extends SimpleMongoConnection
