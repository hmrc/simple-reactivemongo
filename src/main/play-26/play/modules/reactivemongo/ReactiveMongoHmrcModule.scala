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

package play.modules.reactivemongo

import java.util.concurrent.TimeUnit

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api._
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import reactivemongo.api.FailoverStrategy
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ReactiveMongoHmrcModule extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind(classOf[ReactiveMongoComponent]).to[ReactiveMongoComponentImpl].eagerly()
    )
}

/**
  * Cake pattern components.
  */
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

  def mongoConnector: MongoConnector =
    _mongoConnector.getOrElse(throw new Exception("ReactiveMongoPlugin error: no MongoConnector available?"))

  Logger.info("ReactiveMongoPlugin starting...")

  val _mongoConnector: Option[MongoConnector] = {

    val mongoConfig: Configuration =
      configuration
        .getOptional[Configuration]("mongodb")
        .orElse(configuration.getOptional[Configuration](s"${environment.mode}.mongodb"))
        .orElse(configuration.getOptional[Configuration](s"${Mode.Dev}.mongodb"))
        .getOrElse(throw new Exception("The application does not contain required mongodb configuration"))

    mongoConfig.getOptional[String]("uri").map { uri =>
      mongoConfig.getOptional[Int]("channels").foreach { _ =>
        Logger.warn(
          "the mongodb.channels configuration key has been removed and is now ignored. " +
            "Please use the mongodb URL option described here: " +
            "https://docs.mongodb.org/manual/reference/connection-string/#connections-connection-options. " +
            "https://github.com/ReactiveMongo/ReactiveMongo/blob/0.11.3/driver/src/main/scala/api/api.scala#L577"
        )
      }

      val failoverStrategy: Option[FailoverStrategy] =
        mongoConfig.getOptional[Configuration]("failoverStrategy") match {
          case Some(fs: Configuration) =>
            val initialDelay: FiniteDuration = fs
              .getOptional[Long]("initialDelayMsecs")
              .map(delay => new FiniteDuration(delay, TimeUnit.MILLISECONDS))
              .getOrElse(FailoverStrategy().initialDelay)
            val retries: Int = fs.getOptional[Int]("retries").getOrElse(FailoverStrategy().retries)

            Some(
              FailoverStrategy().copy(
                initialDelay = initialDelay,
                retries      = retries,
                delayFactor  = DelayFactor(fs.getOptional[Configuration]("delay"))
              )
            )
          case _ => None
        }

      MongoConnector(uri, failoverStrategy)
    }
  }

  lifecycle.addStopHook { () =>
    Future.successful {
      Logger.info("ReactiveMongoPlugin stops, closing connections...")
      _mongoConnector.foreach(_.close())
    }
  }
}

private[reactivemongo] object DelayFactor {

  import scala.math.pow

  def apply(delay: Option[Configuration]): Int => Double =
    delay match {
      case Some(df: Configuration) =>
        val delayFactor = df.getOptional[Double]("factor").getOrElse(1.0)

        df.getOptional[String]("function") match {
          case Some("linear")      => linear(delayFactor)
          case Some("exponential") => exponential(delayFactor)
          case Some("static")      => static(delayFactor)
          case Some("fibonacci")   => fibonacci(delayFactor)
          case unsupported =>
            throw new PlayException(
              "ReactiveMongoPlugin Error",
              s"Invalid Mongo configuration for delay function: unknown '$unsupported' function"
            )
        }
      case _ => FailoverStrategy().delayFactor
    }

  private def linear(f: Double): Int => Double = n => n * f

  private def exponential(f: Double): Int => Double = n => pow(n, f)

  private def static(f: Double): Int => Double = n => f

  private def fibonacci(f: Double): Int => Double = n => f * (fib take n).last

  def fib: Stream[Long] = {
    def tail(h: Long, n: Long): Stream[Long] = h #:: tail(n, h + n)
    tail(0, 1)
  }
}
