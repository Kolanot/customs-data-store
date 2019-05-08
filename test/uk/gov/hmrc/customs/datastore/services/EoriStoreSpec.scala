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

import org.scalatest.{BeforeAndAfterEach, MustMatchers, WordSpec}
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
  val eori1 = "EORI00000001"
  val eori2 = "EORI00000002"
  val eori3 = "EORI00000003"
  val eori4 = "EORI00000004"
  val eori5 = "EORI00000005"
  val eori6 = "EORI00000006"

  val history1 = EoriHistory(eori1, Some("2001-01-20T00:00:00Z"), None)
  val history2 = EoriHistory(eori2, Some("2002-01-20T00:00:00Z"), None)
  val history3 = EoriHistory(eori3, Some("2003-01-20T00:00:00Z"), None)
  val history4 = EoriHistory(eori4, Some("2004-01-20T00:00:00Z"), None)
  val history5 = EoriHistory(eori5, Some("2005-01-20T00:00:00Z"), None)
  val history6 = EoriHistory(eori6, Some("2006-01-20T00:00:00Z"), None)


  "EoriStore" should {

    "add and update eoris" in {
      val furueResult = for {
        _ <- cache.saveEoris(Seq(history1, history2))
        _ <- cache.saveEoris(Seq(history5, history6)) //To see if the select works correctly
        eoris1 <- cache.getEori(history1.eori)
        eoris2 <- cache.getEori(history2.eori)
        _ <- cache.saveEoris(Seq(history1, history3))
        eoris3 <- cache.getEori(history1.eori)
        eoris4 <- cache.getEori(history3.eori)
        _ <- cache.saveEoris(Seq(history3, history4))
        eoris5 <- cache.getEori(history3.eori)
        eoris6 <- cache.getEori(history4.eori)
        eoris7 <- cache.getEori(history5.eori)
        eoris8 <- cache.getEori(history6.eori)
      } yield (eoris1, eoris2, eoris3, eoris4, eoris5, eoris6, eoris7, eoris8)

      val result = await(furueResult)
      result._1 mustBe Some(EoriHistoryResponse(Seq(history1, history2)))
      result._2 mustBe Some(EoriHistoryResponse(Seq(history1, history2)))
      result._3 mustBe Some(EoriHistoryResponse(Seq(history1, history3)))
      result._4 mustBe Some(EoriHistoryResponse(Seq(history1, history3)))
      result._5 mustBe Some(EoriHistoryResponse(Seq(history3, history4)))
      result._6 mustBe Some(EoriHistoryResponse(Seq(history3, history4)))
      result._7 mustBe Some(EoriHistoryResponse(Seq(history5, history6)))
      result._8 mustBe Some(EoriHistoryResponse(Seq(history5, history6)))
    }

  }

}
