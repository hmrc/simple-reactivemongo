/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}
import play.api.{Configuration, PlayException}
import reactivemongo.api.FailoverStrategy

class DelayFactorSpec extends WordSpec with Matchers {

  "Configuration key 'delay'" should {
    "default to FailoverStrategy#delayFactor if not set" in {
      DelayFactor(None).apply(1) shouldBe FailoverStrategy().delayFactor(1)
    }

    "return the 'factor' value for static function" in {
      val conf = Some(Configuration.from(Map("factor" -> 2.0, "function" -> "static")))

      DelayFactor(conf).apply(9999) shouldBe 2.0
    }

    "return the default 'factor' value" in {
      val conf = Some(Configuration.from(Map("function" -> "static")))

      DelayFactor(conf).apply(9999) shouldBe 1.0
    }

    "return linear value" in {
      val delay = DelayFactor(Some(Configuration.from(Map("factor" -> 1.25, "function" -> "linear"))))
      val expected = Vector(1.25, 2.5, 3.75, 5.0, 6.25, 7.5, 8.75, 10.0, 11.25, 12.5)

      val results = 1 to 10 map delay

      results shouldBe expected
    }

    "return exponential value" in {
      val conf = Some(Configuration.from(Map("factor" -> 2.0, "function" -> "exponential")))

      DelayFactor(conf).apply(2) shouldBe 4.0
    }

    "return fibonacci value" in {
      val delay = DelayFactor(Some(Configuration.from(Map("factor" -> 1.0, "function" -> "fibonacci"))))
      val expected = Vector(0.0, 1.0, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 34.0)

      val results = 1 to 10 map delay

      results shouldBe expected
    }

    "fail for unknown function" in {
      val conf = Some(Configuration.from(Map("function" -> "unknown")))

      intercept[PlayException] {
        DelayFactor(conf)
      }
    }
  }
}
