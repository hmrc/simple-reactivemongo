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

package play.modules.reactivemongo

import java.net.{InetAddress, Socket}

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api._
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import reactivemongo.api.MongoConnection
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.Future
import scala.util.{Failure, Try}

class ReactiveMongoHmrcModule extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind(classOf[ReactiveMongoComponent]).to[ReactiveMongoComponentImpl].eagerly()
    )
}

trait ReactiveMongoComponents {
  def reactiveMongoComponent: ReactiveMongoComponent
}

@ImplementedBy(classOf[ReactiveMongoComponentImpl])
trait ReactiveMongoComponent {
  def mongoConnector: MongoConnector
}

@Singleton
class ReactiveMongoComponentImpl @Inject()(
  configuration: Configuration,
  environment: Environment,
  lifecycle: ApplicationLifecycle)
    extends ReactiveMongoComponent {

  Logger.info("ReactiveMongoPlugin starting...")

  private lazy val mongoConfig = new MongoConfig(environment, configuration)

  val mongoConnector: MongoConnector = {

    if(mongoConfig.diagnostics) MongoDiagnostics.runAll(mongoConfig)

    MongoConnector(
      mongoConfig.uri,
      mongoConfig.maybeFailoverStrategy,
      mongoConfig.dbTimeout
    )

  }
  Logger.debug(s"ReactiveMongoPlugin: MongoConnector configuration being used: $mongoConnector")

  lifecycle.addStopHook { () =>
    Future.successful {
      Logger.info("ReactiveMongoPlugin stops, closing connections...")
      mongoConnector.close()
    }
  }
}

object MongoDiagnostics {

  def runAll(config: MongoConfig):Unit = Try {
    Logger.info("Running mongo diagnostics")
    logConfig(config)
    validateDNS(config)
    validateNetworkConnectivity(config)
    Logger.info("Mongo diagnostics complete")
  }

  private def logConfig(config: MongoConfig): Unit = {
    MongoConnection.parseURI(config.uri)
      .foreach(puri => {
        Logger.info(s"Mongo auth set: ${puri.authenticate.isDefined}")
        Logger.info(s"Mongo database: ${puri.db.getOrElse("Not Set")}")
        Logger.info(s"Mongo nodelist: ${puri.hosts.mkString(",")}")
      })
  }

  private def validateDNS(config: MongoConfig):Unit = {
    MongoConnection.parseURI(config.uri).foreach { parsedUri =>
      parsedUri.hosts.map(_._1).foreach { host =>
        InetAddress.getAllByName(host).map(_.getAddress.map(_.toInt).mkString(".")).foreach(ip => Logger.info(s"Mongo node [$host] resolves to $ip"))
      }
    }
  }

  private def validateNetworkConnectivity(mongoConfig: MongoConfig) : Unit = Try {
      Logger.info("Testing mongo network connectivity...")
      MongoConnection.parseURI(mongoConfig.uri).foreach { parsedUri =>
        parsedUri.hosts.foreach( hp => validateConnection(hp._1, hp._2))
    }
  }

  private def validateConnection(host:String, port:Int): Unit = Try {
    val s = new Socket(host, port)
    Logger.info(s"[$host:$port](${s.getInetAddress.toString}) connected: ${s.isConnected}, is network reachable: ${Try(s.getInetAddress.isReachable(20)).getOrElse(false)}")
    s.close()
  }.recoverWith {
    case ex: Throwable => {
      Logger.info(s"[$host:$port] Mongo network connectivity failed")
      Failure(ex)
    }
  }

}