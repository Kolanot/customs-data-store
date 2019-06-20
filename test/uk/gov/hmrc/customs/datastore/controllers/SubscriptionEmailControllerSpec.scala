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

package uk.gov.hmrc.customs.datastore.controllers

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.customs.datastore.domain.NotificationEmail
import uk.gov.hmrc.customs.datastore.services.EoriStore
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionEmailControllerSpec extends PlaySpec with MockitoSugar {

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
  implicit val hc: HeaderCarrier = HeaderCarrier()


  class SubscriptionEmailControllerScenario() {
    val mockEoriStore = mock[EoriStore]
    val controller = new SubscriptionEmailController(mockEoriStore)
    val fakeRequest = FakeRequest("GET", "/")
  }

  "GET /verifiedEmail/:eori" should {

    "return emails as JSON for the given eori number" in {
      new SubscriptionEmailControllerScenario() {
        when(mockEoriStore.getEmail(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Option(NotificationEmail(Option("test@test.com")))))

        val result = controller.getEmail("eori")(fakeRequest)
        status(result) mustBe Status.OK
        Json.stringify(contentAsJson(result)) mustBe """{"address":"test@test.com"}"""
      }
    }

    "return empty JSON when emails not found for the given eori number" in {
      new SubscriptionEmailControllerScenario() {
        when(mockEoriStore.getEmail(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val result = controller.getEmail("eori")(fakeRequest)
        status(result) mustBe Status.OK
        Json.stringify(contentAsJson(result)) mustBe """null"""
      }
    }
  }
}
