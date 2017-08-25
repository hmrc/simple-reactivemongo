package uk.gov.hmrc.mongo.json

import reactivemongo.bson._

object ExtraBSONHandlers extends ExtraBSONHandlers

trait ExtraBSONHandlers {

  implicit def MapBSONReader[T](implicit reader: BSONReader[_ <: BSONValue, T]): BSONDocumentReader[Map[String, T]] =
    new BSONDocumentReader[Map[String, T]] {
    def read(doc: BSONDocument): Map[String, T] = {
      doc.elements.collect {
        case BSONElement(key, value) => value.seeAsOpt[T](reader) map {
          ov => (key, ov)
        }
      }.flatten.toMap
    }
  }

  implicit def MapBSONWriter[T](implicit writer: BSONWriter[T, _ <: BSONValue]): BSONDocumentWriter[Map[String, T]] = new BSONDocumentWriter[Map[String, T]] {
    def write(doc: Map[String, T]): BSONDocument = {
      BSONDocument(doc.toTraversable map (t => (t._1, writer.write(t._2))))
    }
  }

}
