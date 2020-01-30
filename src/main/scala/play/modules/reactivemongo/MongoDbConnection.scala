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

import play.api.Play
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.mongo.MongoConnector

@deprecated("Static reference to running play app. Inject ReactiveMongoComponent instead.", "7.0.0")
trait MongoDbConnection {

  lazy val mongoConnector: MongoConnector = Play.current.injector.instanceOf[ReactiveMongoComponent].mongoConnector

  implicit val db: () => DefaultDB = mongoConnector.db
}
