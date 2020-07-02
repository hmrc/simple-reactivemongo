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

import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS

import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.prop.PropertyChecks
import play.api.{Configuration, Environment, Mode}
import reactivemongo.api.FailoverStrategy

import scala.concurrent.duration.FiniteDuration

class MongoConfigSpec extends WordSpec with MockFactory with PropertyChecks {

  private val mode        = Mode.Prod
  private val environment = Environment(new File("."), getClass.getClassLoader, mode)
  private val mongodbConfigKeys = Table(
    "config key",
    "mongodb",
    s"$mode.mongodb",
    s"${Mode.Dev}.mongodb"
  )

  forAll(mongodbConfigKeys) { mongodbConfigKey =>
    "mongoConfig" should {

      s"return 'uri' specified under '$mongodbConfigKey.uri'" in new Setup {
        mongoConfig(s"$mongodbConfigKey.uri" -> uri).uri shouldBe uri
      }

      s"throw exception if 'uri' not specified under '$mongodbConfigKey.uri'" in new Setup {
        intercept[IllegalStateException] {
          mongoConfig(mongodbConfigKey -> Map.empty).uri
        }.getMessage shouldBe "mongodb.uri not defined"
      }

      s"override 'initialDelay' if specified under '$mongodbConfigKey.failoverStrategy.initialDelayMsecs'" in new Setup {
        expectDelayFactorFinderCall(withDelayConfig = None)

        mongoConfig(s"$mongodbConfigKey.failoverStrategy.initialDelayMsecs" -> initialDelayMsecs).maybeFailoverStrategy
          .map(_.initialDelay) shouldBe Some(new FiniteDuration(initialDelayMsecs, MILLISECONDS))
      }

      s"return default 'initialDelay' if not specified under '$mongodbConfigKey.failoverStrategy' but 'failoverStrategy' is present" in new Setup {
        expectDelayFactorFinderCall(withDelayConfig = None)

        mongoConfig(s"$mongodbConfigKey.failoverStrategy" -> Map.empty).maybeFailoverStrategy
          .map(_.initialDelay) shouldBe Some(FailoverStrategy().initialDelay)
      }

      s"override 'retries' if specified under '$mongodbConfigKey.failoverStrategy.retries'" in new Setup {
        expectDelayFactorFinderCall(withDelayConfig = None)

        mongoConfig(s"$mongodbConfigKey.failoverStrategy.retries" -> retries).maybeFailoverStrategy
          .map(_.retries) shouldBe Some(retries)
      }

      s"return default 'retries' if not specified under '$mongodbConfigKey.failoverStrategy' but 'failoverStrategy' is present" in new Setup {
        expectDelayFactorFinderCall(withDelayConfig = None)

        mongoConfig(s"$mongodbConfigKey.failoverStrategy" -> Map.empty).maybeFailoverStrategy
          .map(_.retries) shouldBe Some(FailoverStrategy().retries)
      }

      s"override 'delay' if specified under '$mongodbConfigKey.failoverStrategy.delay'" in new Setup {
        val delayConfig = Map("factor" -> 2.0, "function" -> "linear")

        expectDelayFactorFinderCall(withDelayConfig = Some(Configuration.from(delayConfig)))

        mongoConfig(s"$mongodbConfigKey.failoverStrategy.delay" -> delayConfig).maybeFailoverStrategy
          .map(_.delayFactor) shouldBe Some(delayFunction)
      }

      s"override 'delay' if 'delay' not specified under '$mongodbConfigKey.failoverStrategy' but 'failoverStrategy' is present" in new Setup {
        expectDelayFactorFinderCall(withDelayConfig = None)

        mongoConfig(s"$mongodbConfigKey.failoverStrategy" -> Map.empty).maybeFailoverStrategy
          .map(_.delayFactor) shouldBe Some(delayFunction)
      }

      s"return None for 'maybeFailoverStrategy' if '$mongodbConfigKey.failoverStrategy' not present in config" in new Setup {
        mongoConfig(mongodbConfigKey -> Map.empty).maybeFailoverStrategy shouldBe None
      }

      s"return None for 'dbTimeoutMsecs' if '$mongodbConfigKey.dbTimeoutMsecs' not present in config" in new Setup {
        mongoConfig(mongodbConfigKey -> Map.empty).dbTimeout shouldBe None
      }

      s"override 'dbTimeoutMsecs' if specified under '$mongodbConfigKey.dbTimeoutMsecs'" in new Setup {
        val dbTimeout = mongoConfig(s"$mongodbConfigKey.dbTimeoutMsecs" -> dbTimeoutMsecs).dbTimeout
        dbTimeout shouldBe Some(new FiniteDuration(dbTimeoutMsecs, MILLISECONDS))
      }

      s"return None for 'defaultHeartbeatFrequencyMS' if '$mongodbConfigKey.heartbeatFrequency' not present in config" in new Setup {
        mongoConfig(mongodbConfigKey -> Map.empty).defaultHeartbeatFrequencyMS shouldBe None
      }

      s"override 'defaultHeartbeatFrequencyMS' if specified under '$mongodbConfigKey.defaultHeartbeatFrequencyMS'" in new Setup {
        val value = mongoConfig(s"$mongodbConfigKey.defaultHeartbeatFrequencyMS" -> defaultHeartbeatFrequencyMS).defaultHeartbeatFrequencyMS
        value shouldBe Some(defaultHeartbeatFrequencyMS)
      }

      s"ignore 'platform.mongodb.defaultHeartbeatFrequencyMS' if $mongodbConfigKey.defaultHeartbeatFrequencyMS specified" in new Setup {
        val defaultValue = 999
        val value = mongoConfig("platform.mongodb.defaultHeartbeatFrequencyMS" -> defaultValue, s"$mongodbConfigKey.defaultHeartbeatFrequencyMS" -> defaultHeartbeatFrequencyMS).defaultHeartbeatFrequencyMS
        value shouldBe Some(defaultHeartbeatFrequencyMS)
      }

      s"fallback to 'platform.mongodb.defaultHeartbeatFrequencyMS' if $mongodbConfigKey.defaultHeartbeatFrequencyMS not specified" in new Setup {
        val defaultValue = 999
        val value = mongoConfig("platform.mongodb.defaultHeartbeatFrequencyMS" -> defaultValue, s"$mongodbConfigKey.uri" -> "something").defaultHeartbeatFrequencyMS
        value shouldBe Some(defaultValue)
      }
    }
  }

  private trait Setup {
    private val delayFactorFinder   = mockFunction[Option[Configuration], Int => Double]
    val delayFunction               = mockFunction[Int, Double]
    val uri                         = "mongouri"
    val initialDelayMsecs           = 1234
    val dbTimeoutMsecs              = 1234
    val retries                     = 5
    val defaultHeartbeatFrequencyMS = 4000

    def mongoConfig(configEntries: (String, Any)*): MongoConfig =
      new MongoConfig(environment, Configuration(configEntries: _*), delayFactorFinder)

    def expectDelayFactorFinderCall(withDelayConfig: Option[Configuration]) =
      delayFactorFinder
        .expects(withDelayConfig)
        .returning(delayFunction)
  }
}
