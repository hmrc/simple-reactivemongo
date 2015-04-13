package uk.gov.hmrc.mongo

import org.scalatest.{Matchers, WordSpec}
import org.joda.time.{DateTimeZone, DateTime}

class CreationAndLastModifiedDetailSpec extends WordSpec with Matchers {

  "CreationAndLastModifiedDetail objects" should {
    "be able to update their last updated time" in {
      val crud = CreationAndLastModifiedDetail(
        createdAt = DateTime.parse("2013-10-01T13:21:36.969Z"),
        lastUpdated = DateTime.parse("2013-10-01T13:21:36.969Z")
      )

      val time = DateTime.now.withZone(DateTimeZone.UTC)
      val updated = crud.updated(time)

      crud.lastUpdated should not be (time)
      updated.lastUpdated should be(time)
    }
  }

}
