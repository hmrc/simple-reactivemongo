# simple-reactivemongo

Provides simple serialization for [ReactiveMongo](http://reactivemongo.org) - reactive, asynchronous and non-blocking Scala driver for MongoDB.
This started as a fork of [Play-ReactiveMongo](https://github.com/ReactiveMongo/Play-ReactiveMongo).

## Main features

### JSON <-> BSON conversion

With simple-reactivemongo, you can use directly the embedded JSON library in Play >= 2.1. There is a specialized collection called `JSONCollection` that deals naturally with `JSValue` and `JSObject` instead of ReactiveMongo's `BSONDocument`.

### Abstract Repository class ###

Extending `ResponsiveRepository` will provide you with some often used functionality.

### Built-in JSON convertors (Formats) for often used types ###

Formats for BSONObjectId and Joda time classes are implemented.

## Add simple-reactivemongo to your dependencies

In your project/Build.scala:

```scala
libraryDependencies ++= Seq(
  "uk.gov.hmrc" %% "simple-reactivemongo" % "1.0.0"
)
```

### Configure underlying akka system

ReactiveMongo loads it's configuration from the key `mongo-async-driver`

To change the log level (prevent dead-letter logging for example)

```
mongo-async-driver {
  akka {
    loglevel = WARNING
  }
}
```


## Example of a simple repository class ##

```scala

case class TestObject(aField: String,
                      anotherField: Option[String] = None,
                      crud: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
                      _id: BSONObjectID = BSONObjectID.generate)

object TestObject {
  import ReactiveMongoFormats.objectIdFormats
  val formats = Json.format[TestObject]
}

class SimpleTestRepository(implicit mc: MongoConnector)
      extends ReactiveRepository[TestObject, BSONObjectID]("simpleTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {

  override def ensureIndexes() {}

}

```

