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
  "uk.gov.hmrc" %% "simple-reactivemongo" % "1.1.0",
  "com.typesafe.play" %% "play-json" % "2.2.3" //supports from 2.1.0
)
```

#### Create a Repository class ###

Create a `case class` that represents to serialise to mongo.

Create [JSON Read/Write](http://www.playframework.com/documentation/2.2.x/ScalaJsonCombinators) converters. Or if you are doing nothing special create a companion object for the case class
with an implicit member set by play.api.libs.json.Json.format[A]

Extend [ResponsiveRepository](https://github.com/hmrc/simple-reactivemongo/blob/master/src/main/scala/uk/gov/hmrc/mongo/ReactiveRepository.scala) which will provide you with some commonly used functionality.

If the repository requires any indexes override ```indexes: Seq[Index]``` to provide a sequence of indexes that will be applied. Any errors will be logged should they fail.

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

class SimpleTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[TestObject, BSONObjectID]("simpleTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats) {

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

#### Create a repository class using AtomicsUpdate

The AtomicUpdate trait is a wrapper around the findAndModify command which modifies and returns a single document, using atomic operations. By default, the returned mongo document will include the
modifications made on the update. Please refer to the mongo documentation concerning the commands which can be supplied to the update operations.


To include the trait AtomicsUpdate to your existing repository, follow the steps below.

1) Update your repository to use the AtomicUpdate trait, passing the type of the object being read/written.

```scala
class SimpleTestRepository(implicit mc: MongoConnector)
  extends ReactiveRepository[TestObject, BSONObjectID]("simpleTestRepository", mc.db, TestObject.formats, ReactiveMongoFormats.objectIdFormats)
  with AtomicUpdate[TestObject]
  {
```

2) Include the below override in your class which is extending AtomicUpdate. This function is invoked by AtomicUpdate to decide if the update is either an
 upsert or an update.

```scala
    override def isInsertion(suppliedId: BSONObjectID, returned: AtomicTestObject): Boolean =
      suppliedId.equals(returned.id)
```


The two functions exposed from the AtomicUpdate trait are detailed below which both return the type Future[Option[DatabaseUpdate[T]]], where
DatabaseUpdate encapsulates the update type which can be either Saved (new insert) or Updated (updated record). Please note the document with the modifications
applied on the update command to mongo will be returned.

1) def atomicUpsert(finder: BSONDocument, modifierBson: BSONDocument)
This function will invoke atomicSaveOrUpdate passing the 'upsert' flag as true. Function used to insert a new record.

2)  def atomicSaveOrUpdate(finder: BSONDocument, modifierBson: BSONDocument, upsert: Boolean) 
This function is used to override the upsert parameter.

The parameters for the functions are...

a) finder          -    A BSON finder used to find an existing record.
b) modifierBson    -    The BSON modifier to be applied.
c) upsert          -    If the value is true, a BSONDocument will be added to the modifierBSON to generate the Id field on the collection using BSONObjectID.generate.
                        If the value is false, no additional BSONDocument will be applied to modifierBson. If the 'finder' returns no document, then None will be returned.
                        If unsure if the record already exists, then set the 'upsert' value to true.

Please refer to the unit test AtomicUpdateSpec for simple examples concerning using the trait AtomicUpdate.

For documentation, please refer to http://docs.mongodb.org/manual/reference/command/findAndModify.








