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
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.customs.datastore.domain.{Email, EoriPeriod, TraderData}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global

class EoriStoreSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    eoriStore.drop
  }

  val reactiveMongo = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  val eoriStore = new EoriStore(reactiveMongo)

  val credentialId = Some("123456")
  val eori1 = "EORI00000001"
  val eori2 = "EORI00000002"
  val eori3 = "EORI00000003"
  val eori4 = "EORI00000004"
  val eori5 = "EORI00000005"
  val eori6 = "EORI00000006"


  val period1 = EoriPeriod(eori1, Some("2001-01-20T00:00:00Z"), None)
  val period2 = EoriPeriod(eori2, Some("2002-01-20T00:00:00Z"), None)
  val period3 = EoriPeriod(eori3, Some("2003-01-20T00:00:00Z"), None)
  val period4 = EoriPeriod(eori4, Some("2004-01-20T00:00:00Z"), None)
  val period5 = EoriPeriod(eori5, Some("2005-01-20T00:00:00Z"), None)
  val period6 = EoriPeriod(eori6, Some("2006-01-20T00:00:00Z"), None)



  "EoriStore" should {

    "retrieve trader data with any of its historic eoris" in {

      val traderData1 = TraderData(credentialId, eoriHistory = Seq(period1, period2), emails = Nil)
      val traderData2 = TraderData(credentialId, eoriHistory = Seq(period5, period6), emails = Nil)

      def setupDBWith2Trader(): WriteResult = await {
        eoriStore.insert(traderData1)
        eoriStore.insert(traderData2)
      }
      def getTraderData1WithEori1() = await ( eoriStore.getEori(period1.eori) )
      def getTraderData1WithEori2() = await ( eoriStore.getEori(period2.eori) )

      setupDBWith2Trader()
      getTraderData1WithEori1() mustBe Some(traderData1)
      getTraderData1WithEori2() mustBe Some(traderData1)

    }

    "Gabor's complex test 2" in {
      val futureResult = for {
        eoris1 <- eoriStore.getEori(period1.eori)
        eoris2 <- eoriStore.getEori(period2.eori)
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.getEori(period1.eori)
        eoris4 <- eoriStore.getEori(period3.eori)
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.getEori(period3.eori)
        eoris6 <- eoriStore.getEori(period4.eori)
        eoris7 <- eoriStore.getEori(period5.eori)
        eoris8 <- eoriStore.getEori(period6.eori)
      } yield (eoris1, eoris2, eoris3, eoris4, eoris5, eoris6, eoris7, eoris8)

      val result = await(futureResult)
      result._1 mustBe None
      result._2 mustBe None
      result._3 mustBe Some(TraderData(None, Seq(period1, period3), Seq.empty))
      result._4 mustBe Some(TraderData(None, Seq(period1, period3), Seq.empty))
      result._5 mustBe Some(TraderData(None, Seq(period3, period4), Seq.empty))
      result._6 mustBe Some(TraderData(None, Seq(period3, period4), Seq.empty))
      result._7 mustBe None
      result._8 mustBe None
    }

    "update eoris" in {
      val emails = Seq(Email("babyface", true))
      val furueResult = for {
        _ <- eoriStore.insert(TraderData(credentialId,Seq(period1, period2),emails))
        _ <- eoriStore.insert(TraderData(credentialId,Seq(period5, period6),Nil)) //To see if the select works correctly
        eoris1 <- eoriStore.getEori(period1.eori)
        eoris2 <- eoriStore.getEori(period2.eori)
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.getEori(period1.eori)
        eoris4 <- eoriStore.getEori(period3.eori)
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.getEori(period3.eori)
        eoris6 <- eoriStore.getEori(period4.eori)
        eoris7 <- eoriStore.getEori(period5.eori)
        eoris8 <- eoriStore.getEori(period6.eori)
      } yield (eoris1, eoris2, eoris3, eoris4, eoris5, eoris6, eoris7, eoris8)

      val result = await(furueResult)
      result._1 mustBe Some(TraderData(credentialId,Seq(period1, period2),emails))
      result._2 mustBe Some(TraderData(credentialId,Seq(period1, period2),emails))
      result._3 mustBe Some(TraderData(credentialId,Seq(period1, period3),emails))
      result._4 mustBe Some(TraderData(credentialId,Seq(period1, period3),emails))
      result._5 mustBe Some(TraderData(credentialId,Seq(period3, period4),emails))
      result._6 mustBe Some(TraderData(credentialId,Seq(period3, period4),emails))
      result._7 mustBe Some(TraderData(credentialId,Seq(period5, period6),Seq.empty))
      result._8 mustBe Some(TraderData(credentialId,Seq(period5, period6),Seq.empty))
    }

    "insert email" in {
      pending
//      val expectedEmail = "a.b@example.com"
//      cache.saveEmail(credentialId, expectedEmail)
//
//      val email = for {
//        email <- cache.getEmail(credentialId)
//      } yield email
//
//      val result = await(email)
//
//      result mustBe expectedEmail
    }

    "update email" in {
      pending
    }

  }

}
