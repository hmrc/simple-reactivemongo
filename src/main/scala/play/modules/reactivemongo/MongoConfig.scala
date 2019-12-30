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

import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import play.api._
import reactivemongo.api.FailoverStrategy

import scala.concurrent.duration.FiniteDuration

@Singleton
class MongoConfig(
  environment: Environment,
  configuration: Configuration,
  delayFactorFinder: Option[Configuration] => Int => Double) {

  import Implicits._

  @Inject() def this(environment: Environment, configuration: Configuration) =
    this(environment, configuration, DelayFactor)

  lazy val uri: String = mongoConfig
    .getOptional[String]("uri")
    .getOrElse(throw new IllegalStateException("mongodb.uri not defined"))

  lazy val maybeFailoverStrategy: Option[FailoverStrategy] =
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
            delayFactor  = delayFactorFinder(fs.getOptional[Configuration]("delay"))
          )
        )
      case _ => None
    }

  lazy val dbTimeout: Option[FiniteDuration] =
    mongoConfig.getOptional[Long]("dbTimeoutMsecs")
        .map(dbTimeout => new FiniteDuration(dbTimeout, TimeUnit.MILLISECONDS))

  private lazy val mongoConfig: Configuration = configuration
    .getOptional[Configuration]("mongodb")
    .orElse(configuration.getOptional[Configuration](s"${environment.mode}.mongodb"))
    .orElse(configuration.getOptional[Configuration](s"${Mode.Dev}.mongodb"))
    .getOrElse(throw new Exception("The application does not contain required mongodb configuration"))

  lazy val diagnostics: Boolean = mongoConfig.getBoolean("diagnostics").getOrElse(false)
}

private object DelayFactor extends (Option[Configuration] => Int => Double) {

  import scala.math.pow
  import Implicits._

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

  private def fib: Stream[Long] = {
    def tail(h: Long, n: Long): Stream[Long] = h #:: tail(n, h + n)
    tail(0, 1)
  }
}

private object Implicits {
  // Aligns Configuration API for play 2.5 and 2.6
  implicit class ConfigurationOps(configuration: Configuration) {
    private[reactivemongo] def getOptional[T](key: String)(implicit valueFinder: ValueFinder[T]): Option[T] =
      valueFinder.getOptional(configuration, key)
  }
}
