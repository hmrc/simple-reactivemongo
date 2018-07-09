package uk.gov.hmrc.mongo.json

object JsonExtensions {

  import play.api.libs.json._

  def copyKey(fromPath: JsPath, toPath: JsPath) = __.json.update(toPath.json.copyFrom(fromPath.json.pick))
  def moveKey(fromPath: JsPath, toPath: JsPath) =
    (json: JsValue) => json.transform(copyKey(fromPath, toPath) andThen fromPath.json.prune).get
}
