/*
* Created by Ugo Bataillard on 2014/04/27.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package uk.gov.hmrc.mongo

import reactivemongo.bson._

object ExtraBSONHandlers extends ExtraBSONHandlers

trait ExtraBSONHandlers {

  implicit def MapBSONReader[T](implicit reader: BSONReader[_ <: BSONValue, T]): BSONDocumentReader[Map[String, T]] =
    new BSONDocumentReader[Map[String, T]] {
    def read(doc: BSONDocument): Map[String, T] = {
      doc.elements.collect {
        case (key, value) => value.seeAsOpt[T](reader) map {
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

