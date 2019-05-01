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

import org.scalatest.{BeforeAndAfterEach, FlatSpec, MustMatchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.datastore.domain.{EoriHistory, EoriHistoryResponse}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global

class EoriStoreSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    cache.drop
  }

  val reactiveMongo = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  val cache = new EoriStore(reactiveMongo)
  val eori1 = "EORI12345678"
  val eori2 = "EORI00000002"
  val eori3 = "EORI00000003"

  val eori4 = "EORI00000004"
  val eori5 = "EORI00000005"

  "EoriStore" should {

    "put a single EORI into the database" in {
      val newEori = EoriHistory(eori1, Some("1985-03-20T19:30:51Z"),None)
      val result = for {
        _ <- cache.addEori(newEori)
        storedEori <- cache.getEori(eori1)
      } yield storedEori

      await(result) mustBe Some(EoriHistoryResponse(Seq(newEori)))
    }

    "getEori" in {
      pending
    }

    "addEori" in {
      pending
    }

    "associateEori" in {
      val history1 = EoriHistory(eori1, Some("1985-01-20T19:30:51Z"),None)
      val history2 = EoriHistory(eori2, Some("2000-02-20T19:30:51Z"),None)
      val history3 = EoriHistory(eori3, Some("2015-03-20T19:30:51Z"),None)
      val history4 = EoriHistory(eori4, Some("2019-04-20T19:30:51Z"),None)
      val history5 = EoriHistory(eori5, Some("2019-05-20T19:30:51Z"),None)

      val result = for {
        _ <- cache.addEori(history1)
        _ <- cache.associateEori(eori1, history2)
        _ <- cache.associateEori(eori2, history3)

        _ <- cache.addEori(history4)
        _ <- cache.associateEori(eori4, history5)

        history <- cache.getEori(eori3)
      } yield history

      val expectedHistory = EoriHistoryResponse(Seq(history1, history2, history3))

      await(result) mustBe Some(expectedHistory)
    }
  }

}
