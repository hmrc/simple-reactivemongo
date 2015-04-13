package uk.gov.hmrc.mongo

import reactivemongo.bson.{BSONInteger, BSONString, BSONDocument}
import reactivemongo.core.commands.{CommandError, BSONCommandResultMaker, Command}


case class EnsureIndexDelete(collection: String, index: String) extends Command[Int] {
  override def makeDocuments = BSONDocument(
    "deleteIndexes" -> BSONString(collection),
    "index" -> BSONString(index))

  object ResultMaker extends BSONCommandResultMaker[Int] {
    def apply(document: BSONDocument) = CommandError
      .checkOk(document, Some("deleteIndexes"))
      .filterNot(_.getMessage().contains(s"index not found with name [$index]"))
      .toLeft(document.getAs[BSONInteger]("nIndexesWas").map(_.value.toInt).get)
  }
}
