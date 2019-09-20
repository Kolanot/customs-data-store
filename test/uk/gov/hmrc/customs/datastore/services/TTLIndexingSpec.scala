/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.customs.datastore.services

import org.scalatest.{Assertion, MustMatchers, WordSpec}
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{MongoConnector, ReactiveRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TTLIndexingSpec extends WordSpec with MustMatchers with FutureAwaits with DefaultAwaitTimeout {

  case class TestDataModel(content: String)

  object TestDataModel {
    implicit val testFormat = Json.format[TestDataModel]
  }

  class MongoTTL(mongoConnector: MongoConnector, exp:Long = 10) extends
    ReactiveRepository[TestDataModel, BSONObjectID](
      collectionName = "testCollection",
      mongo = mongoConnector.db,
      domainFormat = TestDataModel.testFormat,
      idFormat = ReactiveMongoFormats.objectIdFormats
    ) with TTLIndexing[TestDataModel, BSONObjectID] {

    override val expireAfterSeconds: Long = exp

    override def indexes = Seq(
      Index(Seq("someFieldName" -> IndexType.Ascending), name = Some(ExtraIndexName), unique = true, sparse = true))
  }

  val ExtraIndexName = "theIndex"
  val DefaultIndexName = "_id_"


  def toFuture(condition: Assertion) = Future.successful(condition)

  "MongoTTL" should {
    "add all the indexes on first use" in {
      val mongoConnectorForTest: MongoConnector = MongoConnector("mongodb://127.0.0.1:27017/test-ttl")
      val testDb =

      await(for {
        testDb <- Future.successful(new MongoTTL(mongoConnectorForTest))
        _ <- Future.successful(Thread.sleep(300))  //Mongodb needs a bit of time to add the indexes
        indexes <- testDb.collection.indexesManager.list()
        _ <- toFuture(indexes.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex",ExtraIndexName))
        _ <- testDb.drop
      } yield ())
    }

    "work if expireAfterSeconds did not change" in {
      val mongoConnectorForTest: MongoConnector = MongoConnector("mongodb://127.0.0.1:27017/test-ttl")

      await(for {
        testDb1 <- Future.successful(new MongoTTL(mongoConnectorForTest,10))
        _ <- Future.successful(Thread.sleep(500))  //Mongodb needs a bit of time to add the indexes
        indexes1 <- testDb1.collection.indexesManager.list()
        _ <- toFuture(indexes1.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex",ExtraIndexName))
        _ <- toFuture( indexes1.find(index => index.eventualName == "lastUpdatedIndex").map(testDb1.getExpireAfterSecondsOptionOf) mustBe Some(10))
        testDb2 <- Future.successful(new MongoTTL(mongoConnectorForTest,10))
        _ <- Future.successful(Thread.sleep(500))  //Mongodb needs a bit of time to add the indexes
        indexes2 <- testDb2.collection.indexesManager.list()
        _ <- toFuture(indexes2.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex",ExtraIndexName))
        _ <- toFuture( indexes2.find(index => index.eventualName == "lastUpdatedIndex").map(testDb2.getExpireAfterSecondsOptionOf) mustBe Some(10))
        _ <- testDb1.drop
        _ <- testDb2.drop
      } yield ())
    }

    "Update the expireAfterSeconds if it has changed" in {
      val mongoConnectorForTest: MongoConnector = MongoConnector("mongodb://127.0.0.1:27017/test-ttl")

      await(for {
        testDb1 <- Future.successful(new MongoTTL(mongoConnectorForTest,10))
        _ <- Future.successful(Thread.sleep(500))  //Mongodb needs a bit of time to add the indexes
        indexes1 <- testDb1.collection.indexesManager.list()
        _ <- toFuture(indexes1.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex",ExtraIndexName))
        _ <- toFuture( indexes1.find(index => index.eventualName == "lastUpdatedIndex").map(testDb1.getExpireAfterSecondsOptionOf) mustBe Some(10))
        testDb2 <- Future.successful(new MongoTTL(mongoConnectorForTest,20))
        _ <- Future.successful(Thread.sleep(500))  //Mongodb needs a bit of time to add the indexes
        indexes2 <- testDb2.collection.indexesManager.list()
        _ <- toFuture(indexes2.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex",ExtraIndexName))
        _ <- toFuture( indexes2.find(index => index.eventualName == "lastUpdatedIndex").map(testDb2.getExpireAfterSecondsOptionOf) mustBe Some(20))
        _ <- testDb1.drop
        _ <- testDb2.drop
      } yield ())
    }

  }
}


/*
import play.api.libs.json.{JsObject, JsValue, Json}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.mongo.{MongoSpecSupport, ReactiveRepository}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext
import scala.util.Random
import org.scalatest.concurrent.Eventually
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, Json}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONArray, BSONDocument, BSONLong, BSONObjectID}
import uk.gov.hmrc.customs.datastore.services.MongoSpecSupport
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

class TTLIndexingISpec extends MongoSpec with Eventually {

  val ttl = 12345789

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .build()

  case class TestCaseClass(x: String, y: Int)

  object TestCaseClass {
    implicit val format: Format[TestCaseClass] = Json.format[TestCaseClass]
  }

  class TestTTLRepository extends ReactiveRepository[TestCaseClass, BSONObjectID](collectionName = "test-collection", mongo, TestCaseClass.format)
    with TTLIndexing[TestCaseClass, BSONObjectID] {

    override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
      for{
        ttl <- ensureTTLIndexes
        i <- super.ensureIndexes
      } yield ttl ++ i
    }

    override val ttl: Long = 12345789
  }

  class Setup {
    val repo = new TestTTLRepository

    repo.awaitDrop
  }

  class SetupWithIndex(index: Index){
    val repo = new TestTTLRepository

    repo.awaitDrop
    await(repo.collection.indexesManager.create(index))
  }

  "A TTLIndex" should {

    val ttlIndexName = "lastUpdatedIndex"
    val otherTTLValue = 999

    def buildTTLIndex(ttl: Int) = Index(
      key = Seq("lastUpdated" -> IndexType.Ascending),
      name = Some(ttlIndexName),
      options = BSONDocument("expireAfterSeconds" -> BSONLong(ttl))
    )

    "be applied whenever ensureIndexes is called with the correct expiration value" ignore new Setup {

      repo.listIndexes.size shouldBe 0

      await(repo.ensureIndexes)

      repo.listIndexes.size shouldBe 2

      val ttlIndex: Index = repo.findIndex(ttlIndexName).get
      ttlIndex.eventualName shouldBe ttlIndexName
      ttlIndex.options.elements.head shouldBe BSONDocument("expireAfterSeconds" -> BSONLong(ttl)).elements.head
    }

    "not change when ensureIndexes is called when the expiration value hasn't changed" ignore new Setup {

      repo.listIndexes.size shouldBe 0

      await(repo.ensureIndexes)

      repo.listIndexes.size shouldBe 2

      val ttlIndex: Index = repo.findIndex(ttlIndexName).get
      ttlIndex.eventualName shouldBe ttlIndexName
      ttlIndex.options.elements.head shouldBe BSONDocument("expireAfterSeconds" -> BSONLong(ttl)).elements.head

      await(repo.ensureIndexes)

      repo.listIndexes.size shouldBe 2

      val ttlIndex2: Index = repo.findIndex(ttlIndexName).get
      ttlIndex2.eventualName shouldBe ttlIndexName
      ttlIndex2.options.elements.head shouldBe BSONDocument("expireAfterSeconds" -> BSONLong(ttl)).elements.head
    }

    "update the existing ttl index if the expiration value has changed" ignore new SetupWithIndex(buildTTLIndex(otherTTLValue)) {

      repo.listIndexes.size shouldBe 2

      val ttlIndex: Index = repo.findIndex(ttlIndexName).get
      ttlIndex.eventualName shouldBe ttlIndexName
      ttlIndex.options.elements.head shouldBe BSONDocument("expireAfterSeconds" -> BSONLong(otherTTLValue)).elements.head

      await(repo.ensureIndexes)

      repo.listIndexes.size shouldBe 2

      val ttlIndex2: Index = repo.findIndex(ttlIndexName).get
      ttlIndex2.eventualName shouldBe ttlIndexName
      ttlIndex2.options.elements.head shouldBe BSONDocument("expireAfterSeconds" -> BSONLong(ttl)).elements.head
    }
  }
}

trait MongoSpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with RichReactiveRepository {
  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  def generateOID: String = {
    val alpha = "abcdef123456789"
    (1 to 24).map(x => alpha(Random.nextInt.abs % alpha.length)).mkString
  }
}

trait RichReactiveRepository {
  self: UnitSpec =>

  import scala.language.implicitConversions

  implicit class MongoTTLOps[T](repo: ReactiveRepository[T, _] with TTLIndexing[T, _])(implicit ec: ExecutionContext) {
    def awaitCount: Int = repo.count
    def awaitInsert(e: T): WriteResult = repo.insert(e)
    def awaitDrop: Boolean = repo.drop
    def awaitEnsureIndexes: Seq[Boolean] = repo.ensureIndexes

    def createIndex(index: Index): WriteResult = repo.collection.indexesManager.create(index)
    def listIndexes: List[Index] = repo.collection.indexesManager.list()
    def dropIndexes: Int = repo.collection.indexesManager.dropAll()
    def findIndex(indexName: String): Option[Index] = listIndexes.find(_.eventualName == indexName)
  }

  class JsObjectHelpers(o: JsObject) {
    def pretty: String = Json.prettyPrint(o)
  }

  implicit def impJsObjectHelpers(o: JsObject): JsObjectHelpers = new JsObjectHelpers(o)
  implicit def toJsObject(v: JsValue): JsObject = v.as[JsObject]
}
*/