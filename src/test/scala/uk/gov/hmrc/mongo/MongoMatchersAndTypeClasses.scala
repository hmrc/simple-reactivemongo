package uk.gov.hmrc.mongo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.enablers.Emptiness
import uk.gov.hmrc.mongo.ReactiveRepository
import scala.concurrent.ExecutionContext.Implicits.global

trait MongoMatchersAndTypeClasses extends ScalaFutures {
  implicit val reactiveRepositoryEmptiness = new Emptiness[ReactiveRepository[_, _]] {
    override def isEmpty(thing: ReactiveRepository[_, _]) = thing.count.futureValue == 0
  }
}
