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

  lazy val helper: ReactiveMongoHelper = MongoConnection.parseURI(mongoConnectionUri) match {
    case Success(MongoConnection.ParsedURI(hosts, options, ignoreOptions, Some(db), auth)) =>
      ReactiveMongoHelper(db, hosts.map(h => h._1 + ":" + h._2), auth.toList, failoverStrategy, options)
    case Success(MongoConnection.ParsedURI(_, _, _, None, _)) =>
      throw new Exception(s"Missing database name in mongodb.uri '$mongoConnectionUri'")
    case Failure(e) => throw new Exception(s"Invalid mongodb.uri '$mongoConnectionUri'", e)
  }

  def close() {
    val f = helper.connection.askClose()(10 seconds)
    Await.ready(f, 10 seconds)
  }

}

case class MongoConnector(mongoConnectionUri: String, failoverStrategy : Option[FailoverStrategy] = None) extends SimpleMongoConnection
