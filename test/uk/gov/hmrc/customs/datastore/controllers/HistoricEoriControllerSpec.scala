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
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.customs.datastore.domain.{EoriPeriod, TraderData}
import uk.gov.hmrc.customs.datastore.services.{ETMPHistoryService, EoriStore}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HistoricEoriControllerSpec extends PlaySpec with MockitoSugar with DefaultAwaitTimeout with FutureAwaits with ScalaFutures {

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
  implicit val hc: HeaderCarrier = HeaderCarrier()


  class HistoricControllerScenario() {
    val mockEoriStore = mock[EoriStore]
    val historyService = mock[ETMPHistoryService]
    val mockAuthConnector = mock[CustomAuthConnector]
    when(mockAuthConnector.authorise[Unit](any(), any())(any(), any())).thenReturn(Future.successful({}))
    val controller = new HistoricEoriController(mockAuthConnector, mockEoriStore, historyService)
    val fakeRequest = FakeRequest("GET", "/")
  }

  "GET /" should {

    "return correct JSON from the controller" in {
      val eori = "GB1234567890"
      val eoriHistory = Seq(EoriPeriod(eori, None, None))
      new HistoricControllerScenario() {
        when(mockEoriStore.getTraderData(is(eori)))
          .thenReturn(Future.successful(Some(TraderData(None,eoriHistory,None))))
        when(historyService.getHistory(is(eori))(any()))
          .thenReturn(Future.successful(Nil))
        val result = controller.getEoriHistory(eori)(fakeRequest)
        status(result) mustBe Status.OK
        Json.stringify(contentAsJson(result)) mustBe """[{"eori":"GB1234567890"}]"""
      }
    }

    "return Eori from the cache, and not from HoDs" in {
      val eori = "GB1234567890"
      val eoriHistory = Seq(EoriPeriod(eori, None, None))
      new HistoricControllerScenario() {
        when(mockEoriStore.getTraderData(is(eori)))
          .thenReturn(Future.successful(Some(TraderData(None,eoriHistory,None))))
        when(historyService.getHistory(is(eori))(any()))
          .thenReturn(Future.successful(Nil))
        await(controller.getEoriHistory(eori)(fakeRequest))
        verify(mockEoriStore).getTraderData(is(eori))
        verify(mockEoriStore, never).insert(any())(any())
        verify(historyService, never).getHistory(is(eori))(any())
      }
    }


    "request Eori history from HoDs when the cache is empty, and save it in the cache" in {
      val eori = "GB00000001"
      val eoriHistory = Seq(EoriPeriod(eori, Some("2001-01-20T00:00:00Z"), None))
      new HistoricControllerScenario() {
        when(mockEoriStore.getTraderData(is(eori)))
          .thenReturn(Future.successful(None))
        when(historyService.getHistory(is(eori))(any()))
          .thenReturn(Future.successful(eoriHistory))
        await(controller.getEoriHistory(eori)(fakeRequest))
        verify(mockEoriStore).getTraderData(is(eori))
        verify(mockEoriStore).insert(any())(any())
        verify(historyService).getHistory(is(eori))(any())
      }
    }

    "return unauthorised when bearer token is not supplied" in {
      val eori = "GB1234567890"
      val eoriHistory = Seq(EoriPeriod(eori, None, None))
      new HistoricControllerScenario() {
        when(mockAuthConnector.authorise(any(), any())(any(), any()))
          .thenReturn(Future.failed(new MissingBearerToken()))
        whenReady(controller.getEoriHistory(eori)(fakeRequest).failed) {
          case ex: Throwable => {
            ex.getMessage mustBe "Bearer token not supplied"
          }
        }
      }
    }

  }

}
