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
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global

class EoriStoreSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    await(eoriStore.drop)
  }

  val reactiveMongo = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  val eoriStore = new EoriStore(reactiveMongo)

  val credentialId = Some("123456")
  val eori1: Eori = "EORI00000001"
  val eori2: Eori = "EORI00000002"
  val eori3: Eori = "EORI00000003"
  val eori4: Eori = "EORI00000004"
  val eori5: Eori = "EORI00000005"
  val eori6: Eori = "EORI00000006"


  val period1 = EoriPeriod(eori1, Some("2001-01-20T00:00:00Z"), None)
  val period2 = EoriPeriod(eori2, Some("2002-01-20T00:00:00Z"), None)
  val period3 = EoriPeriod(eori3, Some("2003-01-20T00:00:00Z"), None)
  val period4 = EoriPeriod(eori4, Some("2004-01-20T00:00:00Z"), None)
  val period5 = EoriPeriod(eori5, Some("2005-01-20T00:00:00Z"), None)
  val period6 = EoriPeriod(eori6, Some("2006-01-20T00:00:00Z"), None)



  "EoriStore" should {

    "retrieve trader information with any of its historic eoris" in {

      val trader1 = TraderData(credentialId, eoriHistory = Seq(period1, period2), emails = Nil)
      val trader2 = TraderData(credentialId, eoriHistory = Seq(period5, period6), emails = Nil)

      def setupDBWith2Trader(): WriteResult = await {
        eoriStore.insert(trader1)
        eoriStore.insert(trader2)
      }
      def getTraderWithEori1() = await ( eoriStore.getEori(period1.eori) )
      def getTraderWithEori2() = await ( eoriStore.getEori(period2.eori) )

      setupDBWith2Trader()
      getTraderWithEori1() mustBe Some(trader1)
      getTraderWithEori2() mustBe Some(trader1)

    }

    "Complex email upsert test with empty database" in {
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

    "Complex email upsert test with preloaded data" in {
      val emails = Seq(Email("a.b@mail.com", true))
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

    "save and retrieve email" in {
      val email = Email("a.b@example.com", false)
      await(eoriStore.saveEmail(eori1, email))

      val result = await(eoriStore.getEmail(eori1))
      result mustBe Seq(email)
    }

    "update email" in {
      val email1 = Email("email1", true)
      val email2 = Email("email2", true)
      val email3 = Email("email3", true)
      await(eoriStore.insert(TraderData(credentialId, Seq(period1, period2),Seq(email1))))

      // save email already in DB
      await(eoriStore.saveEmail(eori1, email1))

      // save email2 to eori2
      await(eoriStore.saveEmail(eori2, email2))

      // get emails with eori1
      val result1 = await(eoriStore.getEmail(period1.eori))
      result1 mustBe Seq(email1, email2)

      // get emails with eori2
      val result2 = await(eoriStore.getEmail(period2.eori))
      result2 mustBe Seq(email1, email2)

      // save email3 to eori2
      await(eoriStore.saveEmail(eori2, email3))

      // get emails with eori1
      val result3 = await(eoriStore.getEmail(period1.eori))
      result3 mustBe Seq(email1, email2, email3)
    }

  }

}
