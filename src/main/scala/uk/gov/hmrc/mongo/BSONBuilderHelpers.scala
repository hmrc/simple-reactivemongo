package uk.gov.hmrc.mongo

import reactivemongo.bson.{BSONArray, BSONDocument}

trait BSONBuilderHelpers {

  def or(search: BSONDocument*): BSONDocument = BSONDocument("$or" -> BSONArray(search))

  def and(search: BSONDocument*): BSONDocument = BSONDocument("$and" -> BSONArray(search))

  def set(value: BSONDocument): BSONDocument = BSONDocument("$set" -> value)

  def setOnInsert(value: BSONDocument): BSONDocument = BSONDocument("$setOnInsert" -> value)

  def addToSet(value: BSONDocument): BSONDocument = BSONDocument("$addToSet" -> value)

}
