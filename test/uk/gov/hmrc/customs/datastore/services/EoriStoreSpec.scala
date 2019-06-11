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
import uk.gov.hmrc.customs.datastore.graphql.{EoriPeriodInput, InputEmail}

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

  val intId: InternalId = "1233444"


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
      val emails = Option(NotificationEmail(Option("a.b@mail.com"), true))
      await(for {
        _ <- eoriStore.insert(TraderData(credentialId,Seq(period1, period2),emails))
        _ <- eoriStore.insert(TraderData(credentialId,Seq(period5, period6),None)) //To see if the select works correctly
        eoris1 <- eoriStore.getTraderData(period1.eori)
        _ <- toFuture(eoris1 mustBe Some(TraderData(credentialId,Seq(period1, period2),emails)))
        eoris2 <- eoriStore.getTraderData(period2.eori)
        _ <- toFuture(eoris2 mustBe Some(TraderData(credentialId,Seq(period1, period2),emails)))
        _ <- eoriStore.saveEoris(Seq(period1, period3))
        eoris3 <- eoriStore.getTraderData(period1.eori)
        _ <- toFuture(eoris3 mustBe Some(TraderData(credentialId,Seq(period1, period3),emails)))
        eoris4 <- eoriStore.getTraderData(period3.eori)
        _ <- toFuture(eoris4 mustBe Some(TraderData(credentialId,Seq(period1, period3),emails)))
        _ <- eoriStore.saveEoris(Seq(period3, period4))
        eoris5 <- eoriStore.getTraderData(period3.eori)
        _ <- toFuture(eoris5 mustBe Some(TraderData(credentialId,Seq(period3, period4),emails)))
        eoris6 <- eoriStore.getTraderData(period4.eori)
        _ <- toFuture(eoris6 mustBe Some(TraderData(credentialId,Seq(period3, period4),emails)))
        eoris7 <- eoriStore.getTraderData(period5.eori)
        _ <- toFuture(eoris7 mustBe Some(TraderData(credentialId,Seq(period5, period6),None)))
        eoris8 <- eoriStore.getTraderData(period6.eori)
        _ <- toFuture(eoris8 mustBe Some(TraderData(credentialId,Seq(period5, period6),None)))
      } yield ())

    }

    "save and retrieve email" in {
      val email = NotificationEmail(Option("a.b@example.com"), false)
      await(eoriStore.saveEmail(eori1, email))

      val result = await(eoriStore.getEmail(eori1))
      result mustBe Some(email)
    }

    "update email" in {
      val email1 = NotificationEmail(Option("email1"), false)
      val email1valid = NotificationEmail(Option("email1"), true)
      val email2 = NotificationEmail(Option("email2"), false)
      val email3 = NotificationEmail(Option("email3"), true)
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
    val email1 = NotificationEmail(Option("one@mail.com"), false)
    val email2 = NotificationEmail(Option("one@mail.com"), true)
    val email3 = NotificationEmail(Option("three@mail.com"), true)

    await(for {
      _ <- eoriStore.insert(TraderData(credentialId, Seq(period1, period2),Option(email1)))
      r1 <- eoriStore.getEmail(period1.eori)
      _ <- toFuture(r1 mustBe Some(email1))
      _ <- eoriStore.saveEmail(period1.eori, email2)
      r2 <- eoriStore.getEmail(period1.eori)
      _ <- toFuture(r2 mustBe Some(email2))
    } yield {})
  }

  "upsertByInternalId" should {
    "work without notification email and eori given" in {
      val traderData1 = TraderData(Option(intId), Seq.empty, Option(NotificationEmail(None, false)) )

      await(for {
        _ <- eoriStore.upsertByInternalId(intId, None, None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe traderData1)
      } yield ())
    }

    "work with email address options given" in {
      val address1 = "a@email.com"
      val email1: InputEmail = InputEmail(Option(address1), None)

      await(for {
        _ <- eoriStore.upsertByInternalId(intId, None, Option(email1))
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe  TraderData(Option(intId), Seq.empty, Option(NotificationEmail(Option(address1), false)) ))
      } yield ())
    }

    "work with isValidated options given" in {
      val address1 = "a@email.com"
      val email1: InputEmail = InputEmail(None, Option(true))

      await(for {
        _ <- eoriStore.upsertByInternalId(intId, None, Option(email1))
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe  TraderData(Option(intId), Seq.empty, Option(NotificationEmail(None, true)) ))
      } yield ())
    }

    "work with email options given" in {
      val address1 = "a@email.com"
      val email1: InputEmail = InputEmail(Option(address1), Option(true))

      await(for {
        _ <- eoriStore.upsertByInternalId(intId, None, Option(email1))
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe  TraderData(Option(intId), Seq.empty, Option(NotificationEmail(Option(address1), true)) ))
      } yield ())
    }

    "work with eori option given" in {
      val eori = "1234567"
      val eoriPeriod: EoriPeriodInput = EoriPeriodInput(eori, None, None)
      await(for {
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori, None, None)), None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori, None, None)), Option(NotificationEmail(None, false))))
      } yield ())
    }

    "work with eori option, validFrom, validUntil given " in {
      val eori = "1234567"
      val validFrom = "20180101"
      val validUntil = "20200101"
      val eoriPeriod: EoriPeriodInput = EoriPeriodInput(eori, Option(validFrom), Option(validUntil))
      await(for {
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori, Option(validFrom), Option(validUntil))), None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori, Option(validFrom), Option(validUntil))), Option(NotificationEmail(None, false))))
      } yield ())
    }

    "work with eori option and validFrom given " in {
      val eori = "1234567"
      val validFrom = "20180101"
      val eoriPeriod: EoriPeriodInput = EoriPeriodInput(eori, Option(validFrom), None)
      await(for {
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori, Option(validFrom), None)), None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori, Option(validFrom), None)), Option(NotificationEmail(None, false))))
      } yield ())
    }

    "work with eori option and validUntil given " in {
      val eori = "1234567"
      val validUntil = "20200101"
      val eoriPeriod: EoriPeriodInput = EoriPeriodInput(eori, None, Option(validUntil))
      await(for {
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori, None, Option(validUntil))), None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori, None, Option(validUntil))), Option(NotificationEmail(None, false))))
      } yield ())
    }

    "replace validFrom and validUntil on a given eori" in {
      val eori1 = "1234567"
      val eoriPeriod: EoriPeriodInput = EoriPeriodInput(eori1, None, None)
      await(for {
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori1, None, None)), None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori1, None, None)), Option(NotificationEmail(None, false))))
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori1, Some("A"), Some("B"))), None)
        r2 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r2.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori1, Some("A"), Some("B"))), Option(NotificationEmail(None, false))))
      } yield ())
    }

    "replace old eori with new eori" in {
      val eori1 = "1234567"
      val eori2 = "1234567890"
      val eoriPeriod: EoriPeriodInput = EoriPeriodInput(eori1, None, None)
      await(for {
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori1, None, None)), None)
        r1 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r1.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori1, None, None)), Option(NotificationEmail(None, false))))
        _ <- eoriStore.upsertByInternalId(intId, Option(EoriPeriodInput(eori2, None, None)), None)
        r2 <- eoriStore.getByInternalId(intId)
        _ <- toFuture(r2.get mustBe TraderData(Option(intId), Seq(EoriPeriod(eori1, None, None), EoriPeriod(eori2, None, None)), Option(NotificationEmail(None, false))))
      } yield ())
    }
  }
}
