/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api._
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.Future

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

  lazy val mongoConnector: MongoConnector = MongoConnector(
    mongoConfig.uri,
    mongoConfig.maybeFailoverStrategy,
    mongoConfig.dbTimeout
  )

  Logger.debug(s"ReactiveMongoPlugin: MongoConnector configuration being used: $mongoConnector")

  lifecycle.addStopHook { () =>
    Future.successful {
      Logger.info("ReactiveMongoPlugin stops, closing connections...")
      mongoConnector.close()
    }
  }
}
