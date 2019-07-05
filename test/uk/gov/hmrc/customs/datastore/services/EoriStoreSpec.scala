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

import org.scalatest.{Assertion, BeforeAndAfterEach, MustMatchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{EoriPeriodInput, InputEmail}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EoriStoreSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    await(eoriStore.drop)
  }

  val reactiveMongo = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  val eoriStore = new EoriStore(reactiveMongo)

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

  def toFuture(condition: Assertion) = Future.successful(condition)

  "EoriStore" should {

    "retrieve trader information with any of its historic eoris" in {

      val trader1 = TraderData(eoriHistory = Seq(period1, period2), notificationEmail = None)
      val trader2 = TraderData(eoriHistory = Seq(period5, period6), notificationEmail = None)

      await(for {
        _ <- eoriStore.insert(trader1)
        _ <- eoriStore.insert(trader2)
        t1 <- eoriStore.findByEori(period1.eori)
        t2 <- eoriStore.findByEori(period2.eori)
        _ <- toFuture(t1 mustBe Some(trader1))
        _ <- toFuture(t2 mustBe Some(trader1))
      } yield ())

    }

    //TODO: replace with simple scenario(s)/test(s) or rename test to reflect purpose
    "Complex email upsert test with empty database" in {
      await(for {
        eoris1 <- eoriStore.findByEori(period1.eori)
        _ <- toFuture(eoris1 mustBe None)
        eoris2 <- eoriStore.findByEori(period2.eori)
        _ <- toFuture(eoris2 mustBe None)
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.findByEori(period1.eori)
        _ <- toFuture(eoris3 mustBe Some(TraderData(Seq(period1, period3), None)))
        _ <- toFuture(eoris3 mustBe Some(TraderData(Seq(period1, period3), None)))
        eoris4 <- eoriStore.findByEori(period3.eori)
        _ <- toFuture(eoris4 mustBe Some(TraderData(Seq(period1, period3), None)))
        _ <- toFuture(eoris4 mustBe Some(TraderData(Seq(period1, period3), None)))
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.findByEori(period3.eori)
        _ <- toFuture(eoris5 mustBe Some(TraderData(Seq(period3, period4), None)))
        _ <- toFuture(eoris5 mustBe Some(TraderData(Seq(period3, period4), None)))
        eoris6 <- eoriStore.findByEori(period4.eori)
        _ <- toFuture(eoris6 mustBe Some(TraderData(Seq(period3, period4), None)))
        _ <- toFuture(eoris6 mustBe Some(TraderData(Seq(period3, period4), None)))
        eoris7 <- eoriStore.findByEori(period5.eori)
        _ <- toFuture(eoris7 mustBe None)
        eoris8 <- eoriStore.findByEori(period6.eori)
        _ <- toFuture(eoris8 mustBe None)
      } yield {})

    }

    //TODO: replace with simple scenario(s)/test(s) or rename test to reflect purpose
    "Complex email upsert test with preloaded data" in {
      val emails = Option(NotificationEmail(Option("a.b@mail.com"), None))
      await(for {
        _ <- eoriStore.insert(TraderData(Seq(period1, period2), emails))
        _ <- eoriStore.insert(TraderData(Seq(period5, period6), None)) //To see if the select works correctly
        eoris1 <- eoriStore.findByEori(period1.eori)
        _ <- toFuture(eoris1 mustBe Some(TraderData(Seq(period1, period2), emails)))
        eoris2 <- eoriStore.findByEori(period2.eori)
        _ <- toFuture(eoris2 mustBe Some(TraderData(Seq(period1, period2), emails)))
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.findByEori(period1.eori)
        _ <- toFuture(eoris3 mustBe Some(TraderData(Seq(period1, period3), emails)))
        eoris4 <- eoriStore.findByEori(period3.eori)
        _ <- toFuture(eoris4 mustBe Some(TraderData(Seq(period1, period3), emails)))
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.findByEori(period3.eori)
        _ <- toFuture(eoris5 mustBe Some(TraderData(Seq(period3, period4), emails)))
        eoris6 <- eoriStore.findByEori(period4.eori)
        _ <- toFuture(eoris6 mustBe Some(TraderData(Seq(period3, period4), emails)))
        eoris7 <- eoriStore.findByEori(period5.eori)
        _ <- toFuture(eoris7 mustBe Some(TraderData(Seq(period5, period6), None)))
        eoris8 <- eoriStore.findByEori(period6.eori)
        _ <- toFuture(eoris8 mustBe Some(TraderData(Seq(period5, period6), None)))
      } yield ())

    }

    "save and retrieve email" in {
      val email = NotificationEmail(Option("a.b@example.com"), None)
      await(eoriStore.saveEmail(eori1, email))

      val result = await(eoriStore.getEmail(eori1))
      result mustBe Some(email)
    }

    "update email" in {
      val email1 = NotificationEmail(Option("email1"), None)
      val email1valid = NotificationEmail(Option("email1"), None)
      val email2 = NotificationEmail(Option("email2"), None)
      val email3 = NotificationEmail(Option("email3"), None)

      def setupDB = await(eoriStore.insert(TraderData(Seq(period1, period2), Option(email1))))

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
    val email1 = NotificationEmail(Option("one@mail.com"), None)
    val email2 = NotificationEmail(Option("one@mail.com"), None)
    val email3 = NotificationEmail(Option("three@mail.com"), None)

    await(for {
      _ <- eoriStore.insert(TraderData(Seq(period1, period2), Option(email1)))
      r1 <- eoriStore.getEmail(period1.eori)
      _ <- toFuture(r1 mustBe Some(email1))
      _ <- eoriStore.saveEmail(period1.eori, email2)
      r2 <- eoriStore.getEmail(period1.eori)
      _ <- toFuture(r2 mustBe Some(email2))
    } yield {})
  }

  "upsertByEori" should {

    "insert eori" in {
      val eoriPeriod = EoriPeriodInput(eori1, None, None)
      val traderData1 = TraderData(Seq(EoriPeriod(eori1, None, None)), None)

      await(for {
        _ <- eoriStore.upsertByEori(eoriPeriod, None)
        r1 <- eoriStore.findByEori(eori1)
        _ <- toFuture(r1.get mustBe traderData1)
      } yield ())
    }

    "insert eori with validFrom and validUntil " in {
      val eoriPeriod = EoriPeriodInput(eori1, Some("date1"), Some("date2"))
      val expected = TraderData(Seq(EoriPeriod(eori1, Some("date1"), Some("date2"))), None)

      await(for {
        _ <- eoriStore.upsertByEori(eoriPeriod, None)
        r1 <- eoriStore.findByEori(eori1)
        _ <- toFuture(r1.get mustBe expected)
      } yield ())
    }

    "insert eori with notification email and timestamp " in {
      val eoriPeriod = EoriPeriodInput(eori1, Some("date1"), Some("date2"))
      val inputEmail = InputEmail(Some("test@email.uk"), Some("timestamp"))
      val expected = TraderData(Seq(EoriPeriod(eori1, Some("date1"), Some("date2"))), Some(NotificationEmail(Some("test@email.uk"), Some("timestamp"))))

      await(for {
        _ <- eoriStore.upsertByEori(eoriPeriod, Some(inputEmail))
        r1 <- eoriStore.findByEori(eori1)
        _ <- toFuture(r1.get mustBe expected)
      } yield ())
    }

    "upsert the validFrom, validUntil, email and timestamp fields " in {
      val eoriPeriod = EoriPeriodInput(eori1, Some("date1"), Some("date2"))
      val inputEmail = InputEmail(Some("original@email.uk"), Some("timestamp1"))
      val expectedTraderDataAfterInsert = TraderData(Seq(EoriPeriod(eori1, Some("date1"), Some("date2"))), Some(NotificationEmail(Some("original@email.uk"), Some("timestamp1"))))

      val updatedEoriPeriod = EoriPeriodInput(eori1, Some("date3"), Some("date4"))
      val updatedInputEmail = InputEmail(Some("updated@email.uk"), Some("timestamp2"))
      val expectedTraderDataAfterUpdate = TraderData(Seq(EoriPeriod(eori1, Some("date3"), Some("date4"))), Some(NotificationEmail(Some("updated@email.uk"), Some("timestamp2"))))

      await(for {
        _ <- eoriStore.upsertByEori(eoriPeriod, Some(inputEmail))
        insertedTraderData <- eoriStore.findByEori(eori1)
        _ <- toFuture(insertedTraderData.get mustBe expectedTraderDataAfterInsert)
        _ <- eoriStore.upsertByEori(updatedEoriPeriod, Some(updatedInputEmail))
        updatedTraderData <- eoriStore.findByEori(eori1)
        _ <- toFuture(updatedTraderData.get mustBe expectedTraderDataAfterUpdate)
      } yield ())
    }

  }


}
