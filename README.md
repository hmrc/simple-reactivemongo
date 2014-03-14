# simple-reactivemongo

Provides simple serialization for [ReactiveMongo](http://reactivemongo.org) - reactive, asynchronous and non-blocking Scala driver for MongoDB.

This started as a fork of [Play-ReactiveMongo](https://github.com/ReactiveMongo/Play-ReactiveMongo) as it seemed like a good idea to refactor out the coupling to a Play application.

With some minimal effort, as the ReactiveMongo people had already done the majority of the work, we felt that adding a base repository class creates a library without some
of the issues the other simpler libraries have.

## Main features

#### CASE CLASS <-> JSON <-> BSON conversion

Simple-reactivemongo uses [Play Json](http://www.playframework.com/documentation/2.2.x/ScalaJson) to serialise/deserialise JSON to/from case classes and
there is a specialized collection called `JSONCollection` that deals naturally with `JSValue` and `JSObject` instead of ReactiveMongo's `BSONDocument`.

#### Add simple-reactivemongo

In your project/Build.scala:

```scala
libraryDependencies ++= Seq(
  "uk.gov.hmrc" %% "simple-reactivemongo" % "1.0.1",
  "com.typesafe.play" %% "play-json" % "2.2.2" //supports from 2.1.0
)
```

#### Create a Repository class ###

Create a `case class` that represents to serialise to mongo.

Create [JSON Read/Write](http://www.playframework.com/documentation/2.2.x/ScalaJsonCombinators)) converters. Or if you are doing nothing special create a companion object for the case class
with an implicit member set by play.api.libs.json.Json.format[A]

Extend `ResponsiveRepository` which will provide you with some commonly used functionality.

If the repository requires any indexes override ensureIndexes() and add them to this method.

If you prefer to drop the underscore for the 'id' field in the domain case class then wrap the domain formats in `ReactiveMongoFormats.mongoEntity`

```scala

case class TestObject(aField: String,
                      anotherField: Option[String] = None,
                      optionalCollection : Option[List[NestedModel]] = None,
                      nestedMapOfCollections : Map[String, List[Map[String, Seq[NestedModel]]]] = Map.empty,
                      crud: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
                      id: BSONObjectID = BSONObjectID.generate)

object TestObject {
  import ReactiveMongoFormats.{objectIdFormats, mongoEntity}

  implicit val nestedModelformats = Json.format[NestedModel]

  implicit val formats = mongoEntity{
    Json.format[TestObject]
  }
}

class SimpleTestRepository(implicit mc: MongoConnector)
      extends ReactiveRepository[TestObject, BSONObjectID]("simpleTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {

  override def ensureIndexes() {
    collection.indexesManager.ensure(Index(Seq("aField" -> IndexType.Ascending), name = Some("aFieldUniqueIdx"), unique = true, sparse = true))
  }

}

```

#### Built-in JSON converters ([Formats](http://www.playframework.com/documentation/2.2.x/ScalaJsonCombinators)) for often used types ###

Formats for BSONObjectId and Joda time classes are implemented (see `uk.gov.hmrc.mongo.ReactiveMongoFormats`)

#### Configure underlying Akka system

ReactiveMongo loads it's configuration from the key `mongo-async-driver`

To change the log level (prevent dead-letter logging for example)

```
mongo-async-driver {
  akka {
    loglevel = WARNING
  }
}
```

