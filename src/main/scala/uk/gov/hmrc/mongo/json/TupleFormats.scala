package uk.gov.hmrc.mongo.json

/**
  * Thanks goes to Alexander Jarvis for his Gist (https://gist.github.com/alexanderjarvis/4595298)
  */
trait TupleFormats {
  import play.api.libs.json._
  import play.api.data.validation._

  implicit def tuple2Reads[B, T1, T2](c: (T1, T2) => B)(implicit aReads: Reads[T1], bReads: Reads[T2]): Reads[B] =
    Reads[B] {
      case JsArray(arr) if arr.size == 2 =>
        for {
          a <- aReads.reads(arr(0))
          b <- bReads.reads(arr(1))
        } yield c(a, b)
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("Expected array of two elements"))))
    }

  implicit def tuple2Writes[T1, T2](implicit aWrites: Writes[T1], bWrites: Writes[T2]): Writes[Tuple2[T1, T2]] =
    new Writes[Tuple2[T1, T2]] {
      def writes(tuple: Tuple2[T1, T2]) = JsArray(Seq(aWrites.writes(tuple._1), bWrites.writes(tuple._2)))
    }

  implicit def tuple2Format[T1, T2](
    implicit aReads: Reads[T1],
    bReads: Reads[T2],
    aWrites: Writes[T1],
    bWrites: Writes[T2]) =
    Format(tuple2Reads[Tuple2[T1, T2], T1, T2]((t1, t2) => (t1, t2)), tuple2Writes[T1, T2])
}

object TupleFormats extends TupleFormats
