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

import org.mockito.ArgumentMatchers.{eq => is, _}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.customs.datastore.domain.{EoriHistory, EoriHistoryResponse}
import uk.gov.hmrc.customs.datastore.services.{ETMPHistoryService, EoriStore}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HistoricEoriControllerSpec extends PlaySpec with MockitoSugar {

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val fakeRequest = FakeRequest("GET", "/")

  "GET /" should {

    "return Eori from the cache" in {
      val eori = "GB1234567890"
      val eoriHistory = Seq(EoriHistory(eori, None, None))

      val mockEoriStore = mock[EoriStore]
      when(mockEoriStore.getEori(is(eori)))
        .thenReturn(Future.successful(Some(EoriHistoryResponse(eoriHistory))))
      val historyService = mock[ETMPHistoryService]
      when(historyService.getHistory(is(eori))(any()))
        .thenReturn(Future.successful(Nil))
      val controller = new HistoricEoriController(mockEoriStore, historyService)
      val result = controller.getEoriHistory(eori)(fakeRequest)
      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJson(eoriHistory)
    }


    "request Eori history from HODS when the cache is empty" in {
      val eori = "GB00000001"
      val eoriHistory = Seq(EoriHistory(eori, Some("2001-01-20T00:00:00Z"), None))

      val mockEoriStore = mock[EoriStore]
      when(mockEoriStore.getEori(is(eori)))
        .thenReturn(Future.successful(None))
      //TODO check to see if saveEoris happened!
      val historyService = mock[ETMPHistoryService]
      when(historyService.getHistory(is(eori))(any()))
        .thenReturn(Future.successful(eoriHistory))
      val controller = new HistoricEoriController(mockEoriStore, historyService)
      val result = controller.getEoriHistory(eori)(fakeRequest)
      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJson(eoriHistory)
    }

  }

}
