/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.{MustMatchers, WordSpec}
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{MongoConnector, ReactiveRepository}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class TTLIndexingSpec extends WordSpec with MustMatchers with FutureAwaits with DefaultAwaitTimeout {

  case class TestDataModel(content: String)

  object TestDataModel {
    implicit val testFormat = Json.format[TestDataModel]
  }

  class MongoTTL(mongoConnector: MongoConnector, exp: Long = 10) extends
    ReactiveRepository[TestDataModel, BSONObjectID](
      collectionName = "testCollection",
      mongo = mongoConnector.db,
      domainFormat = TestDataModel.testFormat,
      idFormat = ReactiveMongoFormats.objectIdFormats
    ) with TTLIndexing[TestDataModel, BSONObjectID] {

    override val expireAfterSeconds: Long = exp

    override def indexes = Seq(
      Index(Seq("someFieldName" -> IndexType.Ascending), name = Some(ExtraIndexName), unique = true, sparse = true))

    @tailrec
    final def waitForIndexes(expectedIndexNames:List[String], expectedExpireAfterSeconds: Int, tryCount:Int = 100): Future[List[Index]] = {
      val indexes = await(collection.indexesManager.list())
      if (tryCount > 0) {
        val actualIndexNames = indexes.map(_.eventualName).sorted
        val actualExpireAfterSeconds = indexes.find(index => index.eventualName == "lastUpdatedIndex").map(getExpireAfterSecondsOptionOf)
        if (actualIndexNames == expectedIndexNames && actualExpireAfterSeconds == Some(expectedExpireAfterSeconds)) {
          Future.successful(indexes)
        } else {
          waitForIndexes(expectedIndexNames, expectedExpireAfterSeconds, tryCount - 1)
        }
      } else {
        Future.successful(indexes)
      }
    }
  }

  val ExtraIndexName = "theIndex"
  val DefaultIndexName = "_id_"


  "MongoTTL" should {
    "add all the indexes on first use" in {
      val mongoConnectorForTest: MongoConnector = MongoConnector("mongodb://127.0.0.1:27017/test-ttl")
        await(for {
          testDb <- successful(new MongoTTL(mongoConnectorForTest))
          indexes <- testDb.waitForIndexes(List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName),10)
          _ <- successful(indexes.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName))
          _ <- testDb.drop
        } yield ())
    }

    "work if expireAfterSeconds did not change" in {
      val mongoConnectorForTest: MongoConnector = MongoConnector("mongodb://127.0.0.1:27017/test-ttl")

      await(for {
        testDb1 <- successful(new MongoTTL(mongoConnectorForTest, 10))
        indexes1 <- testDb1.waitForIndexes(List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName),10)
        _ <- successful(indexes1.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName))
        _ <- successful(indexes1.find(index => index.eventualName == "lastUpdatedIndex").map(testDb1.getExpireAfterSecondsOptionOf) mustBe Some(10))
        testDb2 <- successful(new MongoTTL(mongoConnectorForTest, 10))
        indexes2 <- testDb2.waitForIndexes(List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName),10)
        _ <- successful(indexes2.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName))
        _ <- successful(indexes2.find(index => index.eventualName == "lastUpdatedIndex").map(testDb2.getExpireAfterSecondsOptionOf) mustBe Some(10))
        _ <- testDb1.drop
        _ <- testDb2.drop
      } yield ())
    }

    "Update the expireAfterSeconds if it has changed" in {
      val mongoConnectorForTest: MongoConnector = MongoConnector("mongodb://127.0.0.1:27017/test-ttl")

      await(for {
        testDb1 <- successful(new MongoTTL(mongoConnectorForTest, 10))
        indexes1 <- testDb1.waitForIndexes(List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName),10)
        _ <- successful(indexes1.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName))
        _ <- successful(indexes1.find(index => index.eventualName == "lastUpdatedIndex").map(testDb1.getExpireAfterSecondsOptionOf) mustBe Some(10))
        testDb2 <- successful(new MongoTTL(mongoConnectorForTest, 20))
        indexes2 <- testDb2.waitForIndexes(List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName),20)
        _ <- successful(indexes2.map(_.eventualName).sorted mustBe List(DefaultIndexName, "lastUpdatedIndex", ExtraIndexName))
        _ <- successful(indexes2.find(index => index.eventualName == "lastUpdatedIndex").map(testDb2.getExpireAfterSecondsOptionOf) mustBe Some(20))
        _ <- testDb1.drop
        _ <- testDb2.drop
      } yield ())
    }

  }
}