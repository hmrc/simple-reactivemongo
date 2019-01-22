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

package uk.gov.hmrc.mongo

import org.scalatest.{Matchers, WordSpec}
import org.joda.time.{DateTime, DateTimeZone}

class CreationAndLastModifiedDetailSpec extends WordSpec with Matchers {

  "CreationAndLastModifiedDetail objects" should {
    "be able to update their last updated time" in {
      val crud = CreationAndLastModifiedDetail(
        createdAt   = DateTime.parse("2013-10-01T13:21:36.969Z"),
        lastUpdated = DateTime.parse("2013-10-01T13:21:36.969Z")
      )

      val time    = DateTime.now.withZone(DateTimeZone.UTC)
      val updated = crud.updated(time)

      crud.lastUpdated    should not be (time)
      updated.lastUpdated should be(time)
    }
  }

}
