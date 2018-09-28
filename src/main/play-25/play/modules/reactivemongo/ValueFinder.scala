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

import play.api.Configuration

private trait ValueFinder[T] {
  def getOptional(config: Configuration, key: String): Option[T]
}

private object ValueFinder {
  implicit val configurationValueFinder: ValueFinder[Configuration] = new ValueFinder[Configuration] {
    override def getOptional(config: Configuration, key: String): Option[Configuration] = config.getConfig(key)
  }

  implicit val stringValueFinder: ValueFinder[String] = new ValueFinder[String] {
    override def getOptional(config: Configuration, key: String): Option[String] = config.getString(key)
  }

  implicit val doubleValueFinder: ValueFinder[Double] = new ValueFinder[Double] {
    override def getOptional(config: Configuration, key: String): Option[Double] = config.getDouble(key)
  }

  implicit val longValueFinder: ValueFinder[Long] = new ValueFinder[Long] {
    override def getOptional(config: Configuration, key: String): Option[Long] = config.getLong(key)
  }

  implicit val intValueFinder: ValueFinder[Int] = new ValueFinder[Int] {
    override def getOptional(config: Configuration, key: String): Option[Int] = config.getInt(key)
  }
}
