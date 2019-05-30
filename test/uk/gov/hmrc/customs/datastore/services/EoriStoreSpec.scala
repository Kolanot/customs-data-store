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
import org.scalatest.Assertion
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EoriStoreSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    //await(eoriStore.removeAll())
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

  def toFuture(condition:Assertion) = Future.successful(condition)

  "EoriStore" should {

    "retrieve trader information with any of its historic eoris" in {

      val trader1 = TraderData(credentialId, eoriHistory = Seq(period1, period2), notificationEmail = None)
      val trader2 = TraderData(credentialId, eoriHistory = Seq(period5, period6), notificationEmail = None)

      await(for {
        _ <- eoriStore.insert(trader1)
        _ <- eoriStore.insert(trader2)
        t1 <- eoriStore.getTraderData(period1.eori)
        t2 <- eoriStore.getTraderData(period2.eori)
        _ <- toFuture(t1 mustBe Some(trader1))
        _ <- toFuture(t2 mustBe Some(trader1))
      } yield ())

    }

    //TODO: replace with simple scenario(s)/test(s) or rename test to reflect purpose
    "Complex email upsert test with empty database" in {
      await(for {
        eoris1 <- eoriStore.getTraderData(period1.eori)
        _ <- toFuture(eoris1 mustBe None)
        eoris2 <- eoriStore.getTraderData(period2.eori)
        _ <- toFuture(eoris2 mustBe None)
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.getTraderData(period1.eori)
        _ <- toFuture(eoris3 mustBe Some(TraderData(None, Seq(period1, period3), None)))
        _ <- toFuture(eoris3 mustBe Some(TraderData(None, Seq(period1, period3), None)))
        eoris4 <- eoriStore.getTraderData(period3.eori)
        _ <- toFuture(eoris4 mustBe Some(TraderData(None, Seq(period1, period3), None)))
        _ <- toFuture(eoris4 mustBe Some(TraderData(None, Seq(period1, period3), None)))
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.getTraderData(period3.eori)
        _ <- toFuture(eoris5 mustBe Some(TraderData(None, Seq(period3, period4), None)))
        _ <- toFuture(eoris5 mustBe Some(TraderData(None, Seq(period3, period4), None)))
        eoris6 <- eoriStore.getTraderData(period4.eori)
        _ <- toFuture(eoris6 mustBe Some(TraderData(None, Seq(period3, period4), None)))
        _ <- toFuture(eoris6 mustBe Some(TraderData(None, Seq(period3, period4), None)))
        eoris7 <- eoriStore.getTraderData(period5.eori)
        _ <- toFuture(eoris7 mustBe None)
        eoris8 <- eoriStore.getTraderData(period6.eori)
        _ <- toFuture(eoris8 mustBe None)
      } yield {})

    }

    //TODO: replace with simple scenario(s)/test(s) or rename test to reflect purpose
    "Complex email upsert test with preloaded data" in {
      val emails = Option(NotificationEmail("a.b@mail.com", true))
      val furueResult = for {
        _ <- eoriStore.insert(TraderData(credentialId,Seq(period1, period2),emails))
        _ <- eoriStore.insert(TraderData(credentialId,Seq(period5, period6),None)) //To see if the select works correctly
        eoris1 <- eoriStore.getTraderData(period1.eori)
        eoris2 <- eoriStore.getTraderData(period2.eori)
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.getTraderData(period1.eori)
        eoris4 <- eoriStore.getTraderData(period3.eori)
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.getTraderData(period3.eori)
        eoris6 <- eoriStore.getTraderData(period4.eori)
        eoris7 <- eoriStore.getTraderData(period5.eori)
        eoris8 <- eoriStore.getTraderData(period6.eori)
      } yield (eoris1, eoris2, eoris3, eoris4, eoris5, eoris6, eoris7, eoris8)

      val result = await(furueResult)
      result._1 mustBe Some(TraderData(credentialId,Seq(period1, period2),emails))
      result._2 mustBe Some(TraderData(credentialId,Seq(period1, period2),emails))
      result._3 mustBe Some(TraderData(credentialId,Seq(period1, period3),emails))
      result._4 mustBe Some(TraderData(credentialId,Seq(period1, period3),emails))
      result._5 mustBe Some(TraderData(credentialId,Seq(period3, period4),emails))
      result._6 mustBe Some(TraderData(credentialId,Seq(period3, period4),emails))
      result._7 mustBe Some(TraderData(credentialId,Seq(period5, period6),None))
      result._8 mustBe Some(TraderData(credentialId,Seq(period5, period6),None))
    }

    "save and retrieve email" in {
      val email = NotificationEmail("a.b@example.com", false)
      await(eoriStore.saveEmail(eori1, email))

      val result = await(eoriStore.getEmail(eori1))
      result mustBe Some(email)
    }

    "update email" in {
      val email1 = NotificationEmail("email1", false)
      val email1valid = NotificationEmail("email1", true)
      val email2 = NotificationEmail("email2", false)
      val email3 = NotificationEmail("email3", true)
      def setupDB = await(eoriStore.insert(TraderData(credentialId, Seq(period1, period2),Option(email1))))
      def saveEmailAlreadyInDB = await(eoriStore.saveEmail(eori1, email1))
      def saveEmail2ToEori2 = await(eoriStore.saveEmail(eori1, email2))
      def saveEmail3ToEori2 = await(eoriStore.saveEmail(eori1, email3))
      def updateEmailsValidation = await(eoriStore.saveEmail(eori1, email1valid))
      def getEmailsWithEori1 = await(eoriStore.getEmail(period1.eori))
      def getEmailsWithEori2 = await(eoriStore.getEmail(period2.eori))

      setupDB
      saveEmailAlreadyInDB
      updateEmailsValidation

      val result1 = getEmailsWithEori1
      result1 mustBe Some(email1valid)

      saveEmail2ToEori2
      val result2 = getEmailsWithEori1
      result2 mustBe Some(email2)

      saveEmail3ToEori2
      val result3 = getEmailsWithEori1
      result3 mustBe Some(email3)
    }
  }

  "update email with isValidated" in {
    val email1 = NotificationEmail("one@mail.com", false)
    val email2 = NotificationEmail("one@mail.com", true)
    val email3 = NotificationEmail("three@mail.com", true)

    await(for {
      _ <- eoriStore.insert(TraderData(credentialId, Seq(period1, period2),Option(email1)))
      r1 <- eoriStore.getEmail(period1.eori)
      _ <- toFuture(r1 mustBe Some(email1))
      _ <- eoriStore.saveEmail(period1.eori, email2)
      r2 <- eoriStore.getEmail(period1.eori)
      _ <- toFuture(r2 mustBe Some(email2))
    } yield {})
  }

}
