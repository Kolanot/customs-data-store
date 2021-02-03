/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.customs.datastore.controllers

import org.mockito.ArgumentMatcher
import org.mockito.Matchers.{any, argThat}
import org.mockito.Mockito.{verifyZeroInteractions, when}
import play.api.inject
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.customs.datastore.domain.onwire.MdgSub09DataModel
import uk.gov.hmrc.customs.datastore.domain.{NotificationEmail, TraderData}
import uk.gov.hmrc.customs.datastore.services.{EoriStore, SubscriptionInfoService}
import uk.gov.hmrc.customs.datastore.utils.SpecBase

import java.time.LocalDate
import scala.concurrent.Future


class VerifiedEmailControllerSpec extends SpecBase {

  "getVerifiedEmail" should {
    "return Not Found if no data is found in the cache and SUB09 returns no email" in new Setup {
      when(mockEoriStore.findByEori(any())).thenReturn(Future.successful(None))
      when(mockSubscriptionInfoService.getSubscriberInformation(any())(any())).thenReturn(Future.successful(None))

      val request = FakeRequest(GET, routes.VerifiedEmailController.getVerifiedEmail(testEori).url)

      running(app){
        val result = route(app, request).value
        status(result) mustBe 404
      }
    }

    "return InternalServerError if the updating of trader data fails" in new Setup {
      when(mockEoriStore.findByEori(any())).thenReturn(Future.successful(None))
      when(mockEoriStore.upsertByEori(any(),any())).thenReturn(Future.successful(false))
      when(mockSubscriptionInfoService.getSubscriberInformation(any())(any()))
        .thenReturn(Future.successful(Some(MdgSub09DataModel(Some(testAddress), Some(testTime.toString)))))

      val request = FakeRequest(GET, routes.VerifiedEmailController.getVerifiedEmail(testEori).url)

      running(app){
        val result = route(app, request).value
        status(result) mustBe 500
      }
    }

    "return InternalServerError if the retrieving of the trader's data fails after updating the cache with the SUB09 data" in new Setup {
      when(mockEoriStore.findByEori(any())).thenReturn(Future.successful(None), Future.successful(None))
      when(mockEoriStore.upsertByEori(any(),any())).thenReturn(Future.successful(true))
      when(mockSubscriptionInfoService.getSubscriberInformation(any())(any()))
        .thenReturn(Future.successful(Some(MdgSub09DataModel(Some(testAddress), Some(testTime.toString)))))

      val request = FakeRequest(GET, routes.VerifiedEmailController.getVerifiedEmail(testEori).url)

      running(app){
        val result = route(app, request).value
        status(result) mustBe 500
      }
    }

    "return the email and not call SUB09 if the data is stored in the cache" in new Setup {
      when(mockEoriStore.findByEori(any())).thenReturn(Future.successful(Some(testTraderData)))
      val request = FakeRequest(GET, routes.VerifiedEmailController.getVerifiedEmail(testEori).url)

      running(app){
        val result = route(app, request).value
        status(result) mustBe 200
        contentAsJson(result) mustBe Json.obj("address" -> testAddress, "timestamp" -> testTime.toString)
        verifyZeroInteractions(mockSubscriptionInfoService)
      }
    }

    "return the email and call SUB09 if the data is not stored in the cache and also store the response into the cache" in new Setup {
      when(mockEoriStore.findByEori(any())).thenReturn(Future.successful(None), Future.successful(Some(testTraderData)))
      when(mockEoriStore.upsertByEori(any(),any())).thenReturn(Future.successful(true))
      when(mockSubscriptionInfoService.getSubscriberInformation(any())(any()))
        .thenReturn(Future.successful(Some(MdgSub09DataModel(Some(testAddress), Some(testTime.toString)))))

      val request = FakeRequest(GET, routes.VerifiedEmailController.getVerifiedEmail(testEori).url)

      running(app){
        val result = route(app, request).value
        status(result) mustBe 200
        contentAsJson(result) mustBe Json.obj("address" -> testAddress, "timestamp" -> testTime.toString)
      }
    }
  }

  "updateVerifiedEmail" should {
    "return internal server error if the update failed to populate the cache" in new Setup {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(false))

      val request = FakeRequest(POST, routes.VerifiedEmailController.updateVerifiedEmail().url).withJsonBody(
        Json.obj("eori" -> testEori, "address" -> testAddress)
      )

      running(app){
        val result = route(app, request).value
        status(result) mustBe 500
      }
    }

    "return 400 with malformed request" in new Setup {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))

      val request = FakeRequest(POST, routes.VerifiedEmailController.updateVerifiedEmail().url).withJsonBody(
        Json.obj("invalidKey" -> testEori, "address" -> testAddress)
      )

      running(app){
        val result = route(app, request).value
        status(result) mustBe 400
      }
    }

    "return 204 if the update was successful without a timestamp present" in new Setup {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))

      val request = FakeRequest(POST, routes.VerifiedEmailController.updateVerifiedEmail().url).withJsonBody(
        Json.obj("eori" -> testEori, "address" -> testAddress)
      )

      running(app){
        val result = route(app, request).value
        status(result) mustBe 204
      }
    }

    "return 204 if the update was successful with a timestamp present" in new Setup {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))

      val request = FakeRequest(POST, routes.VerifiedEmailController.updateVerifiedEmail().url).withJsonBody(
        Json.obj("eori" -> testEori, "address" -> testAddress, "timestamp" -> testTime.toString)
      )

      running(app){
        val result = route(app, request).value
        status(result) mustBe 204
      }
    }
  }

  trait Setup {
    val mockEoriStore: EoriStore = mock[EoriStore]
    val mockSubscriptionInfoService: SubscriptionInfoService = mock[SubscriptionInfoService]
    val testEori = "GB12345678912"
    val testTime = LocalDate.now()
    val testAddress = "test@email.com"

    val testNotificationEmail = NotificationEmail(Some(testAddress), Some(testTime.toString))
    val testTraderData = TraderData(Seq.empty, Some(testNotificationEmail))

    lazy val app = application.overrides(
      inject.bind[EoriStore].toInstance(mockEoriStore),
      inject.bind[SubscriptionInfoService].toInstance(mockSubscriptionInfoService)
    ).build()
  }
}
