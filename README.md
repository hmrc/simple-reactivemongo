[DEPRECATED]
=
*Use https://github.com/hmrc/hmrc-mongo instead*

# simple-reactivemongo

[![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

Provides simple serialization for [ReactiveMongo](http://reactivemongo.org) - reactive, asynchronous and non-blocking Scala driver for MongoDB.

This started as a fork of [Play-ReactiveMongo](https://github.com/ReactiveMongo/Play-ReactiveMongo) as it seemed like a good idea to refactor out the coupling to a Play application. However, since version 7.x.x the dependency to the fork has been dropped in favour of the original `reactivemongo` driver.

With some minimal effort, as the ReactiveMongo people had already done the majority of the work, we felt that adding a base repository class creates a library without some
of the issues the other simpler libraries have.

## Upgrading from 7.x.x to 8.x.x?

### Major changes:

* Library is built for Scala 2.12 and Play 2.6, 2.7 and 2.8.
* The deprecated `MongoDbConnection` has been removed (since it depended on a static Play application which is no longer available in Play 2.8). Inject `ReactiveMongoComponent` instead.

## Upgrading from 6.x.x to 7.x.x?

### Major changes:

* The dependency to HMRC's fork of `reactivemongo` has been dropped in favour of the original `reactivemongo` driver. Version 7.x.x depends now on `reactivemongo` 0.16.0.
* It got merged with `play-reactivemongo` so all classes which used to be provided by that library are now in `simple-reactivemongo` (for instance `ReactiveMongoHmrcModule` Play module and `ReactiveMongoComponent`). As a consequence `simple-reactivemongo` should be the only dependency a service would require for interactions with `MongoDB`. There will be no new version of `play-reactivemongo` depending on the `simple-reactivemongo` 7.x.x or above.
* There are two versions of the library released for two versions of Play. So 7.x.x-play-25 and 7.x.x-play-26 are compatible with `Play` 2.5 and 2.6 respectively.
* you may get more warnings if your connection string includes nonexistent hosts, the solution then is to fix the connection string and only keep valid hosts
* `reactivemongo` 0.16.0 brings some breaking changes which should be addressed on upgraded to 7.x.x. More can be found [here](http://reactivemongo.org/releases/0.1x/documentation/release-details.html#breaking-changes).
* `MongoDbConnection` has been deprecated and `ReactiveMongoComponent` is the new provider of `MongoConnector` instances.
* `ReactiveRepository` was enriched with new `findAndUpdate` and `count` methods.
* `AtomicUpdate` trait has become deprecated as similar functionality is provided now by `ReactiveRepository.findAndUpdate`.

## Upgrading from 5.x.x to 6.x.x?

With version 6.x.x of simple-reactivemongo, we are moving to the latest version of reactivemongo which comes with a few braking changes documented here:
http://reactivemongo.org/releases/0.12/documentation/release-details.html#breaking-changes

You will most likely encounter some of the following issues. Please have a look on how we recommend to fix them.

#### No Json serializer as JsObject found

Due to keeping parity with upstream, companion objects for inner classes do not have the ImplicitBSONHandlers handlers.
If you see an error like this:
```No Json serializer as JsObject found for type reactivemongo.bson.BSONDocument. Try to implement an implicit OWrites or OFormat for this type.```
Try adding the following import
```scala
import reactivemongo.play.json.ImplicitBSONHandlers._
```

#### WriteResult is no longer an Exception

The type hierarchy of the trait `WriteResult` has changed in new version of reactivemongo.
It’s no longer an Exception. As it no longer represents errors in the public API, the following properties have been removed: `errmsg`, `hasErrors`, `inError` and `message`.

simple-reactivemongo previously exposed `inError`, something that is no longer possible.

Also, given that the `LastError` was part of the `WriteResult`, it used to be returned in a `Future.Success`, now it's returned in a `Future.Failure`.

If you have something like this in your code:
```scala
collection.doSomething([...]) map {
    case lastError if lastError.inError => // handle the error
    case _ => // return successfully
}
```

Now, you have to do this:
```scala
collection.doSomething([...]) map {
    case _ => // return successfully
} recover {
   case lastError => // handle the error
}
```

## Main features

#### CASE CLASS <-> JSON <-> BSON conversion

Simple-reactivemongo uses [Play Json](http://www.playframework.com/documentation/2.3.x/ScalaJson) to serialise/deserialise JSON to/from case classes and
there is a specialized collection called `JSONCollection` that deals naturally with `JSValue` and `JSObject` instead of ReactiveMongo's `BSONDocument`.

#### Add simple-reactivemongo

In your project/build.sbt:

```scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")

libraryDependencies ++= Seq(
  "uk.gov.hmrc" %% "simple-reactivemongo" % "[INSERT_VERSION]"
)
```

* *For Play 2.5.x and above use versions <=7.x.x-play-25*
* *For Play 2.6.x and above use versions <=7.x.x-play-26*
* *For Java 7 and Play 2.3.x use versions <=4.1.0*


#### Create a Repository class ###

Create a `case class` that represents data model to be serialized/deserialised to `MongoDB`.

Create [JSON Read/Write](http://www.playframework.com/documentation/2.2.x/ScalaJsonCombinators) converters. Or if you are doing nothing special create a companion object for the case class
with an implicit member set by play.api.libs.json.Json.format[A]

Extend [ReactiveRepository](https://github.com/hmrc/simple-reactivemongo/blob/master/src/main/scala/uk/gov/hmrc/mongo/ReactiveRepository.scala) which will provide you with some commonly used functionality.

If the repository requires any indexes override ```indexes: Seq[Index]``` to provide a sequence of indexes that will be applied. Any errors will be logged and instantiation of the repository should fail.

If you prefer to drop the underscore for the 'id' field in the domain case class then wrap the domain formats in `ReactiveMongoFormats.mongoEntity`


```scala

case class TestObject(aField: String,
                      anotherField: Option[String] = None,
                      optionalCollection: Option[List[NestedModel]] = None,
                      nestedMapOfCollections: Map[String, List[Map[String, Seq[NestedModel]]]] = Map.empty,
                      modifiedDetails: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
                      jsValue: Option[JsValue] = None,
                      location : Tuple2[Double, Double] = (0.0, 0.0),
                      id: BSONObjectID = BSONObjectID.generate) {

  def markUpdated(implicit updatedTime: DateTime) = copy(
    modifiedDetails = modifiedDetails.updated(updatedTime)
  )

}

object TestObject {

  import ReactiveMongoFormats.{objectIdFormats, mongoEntity}

  implicit val formats = mongoEntity {

    implicit val locationFormat = TupleFormats.tuple2Format[Double, Double]

    implicit val nestedModelformats = Json.format[NestedModel]

    Json.format[TestObject]
  }
}

import javax.inject._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.ReactiveRepository
import play.modules.reactivemongo.ReactiveMongoComponent

@Singleton
class SimpleTestRepository @Inject()(mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[TestObject, BSONObjectID](
      collectionName = "simpleTestRepository",
      mongo          = mongoComponent.mongoConnector.db,
      domainFormat   = TestObject.formats,
      idFormat       = ReactiveMongoFormats.objectIdFormats
    ) {

  import reactivemongo.api.indexes.IndexType
  import reactivemongo.api.indexes.Index

  override def indexes: Seq[Index] = Seq(
    Index(Seq("aField" -> IndexType.Ascending), name = Some("aFieldUniqueIdx"), unique = true, sparse = true)
  )
}

```
(See [ReactiveRepositorySpec](https://github.com/hmrc/simple-reactivemongo/blob/master/src/test/scala/uk/gov/hmrc/mongo/ReactiveRepositorySpec.scala) for example usage)

#### Built-in JSON converters ([Formats](http://www.playframework.com/documentation/2.2.x/ScalaJsonCombinators)) for often used types ###

Formats for BSONObjectId and Joda time classes are implemented (see [ReactiveMongoFormats](https://github.com/hmrc/simple-reactivemongo/blob/master/src/main/scala/uk/gov/hmrc/mongo/ReactiveMongoFormats.scala))

#### Configuration Options

There is a default timeout of 10 seconds when making connections with Mongo. This value is configurable by setting the key within your `application.conf`:

`mongodb.dbTimeoutMsecs=10000`

This library also allows setting of a [FailoverStrategy](http://reactivemongo.org/releases/0.11/documentation/advanced-topics/failoverstrategy.html) via configuration, which defines if (and how) database operation should be retried if ReactiveMongo can't communicate with the cluster.

For example, this is the default failover strategy enabled by ReactiveMongo. It retries 10 times at these intervals: 125ms, 250ms, 375ms, 500ms, 625ms, 750ms, 875ms, 1s, 1125ms, 1250ms

```
mongodb.failoverStrategy {
  retries = 10
  initialDelayMsecs = 100
  delay {
    factor = 1.25
    function = linear
  }
}
```

The delay block has been introduced by this library. `factor` should be a Double, and `function` should be one of `linear`, `static`, `exponential`, or `fibonacci`. For the function definitions, please refer to the code in [the DelayFactor object](https://github.com/hmrc/simple-reactivemongo/blob/master/src/main/scala/play/modules/reactivemongo/MongoConfig.scala#L74).

`mongodb.defaultHeartbeatFrequencyMS` allows setting of the connection string property of the same name. If both are set, the connection string property takes precedence.

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
