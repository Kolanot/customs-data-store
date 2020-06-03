/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{verify, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import play.api.libs.json.{JsString, JsValue}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.onwire.{MdgSub09DataModel, Sub09Response}
import uk.gov.hmrc.http.{HeaderCarrier, ServiceUnavailableException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}


class SubscriptionInfoServiceSpec extends WordSpec with MustMatchers with MockitoSugar with MockitoAnswerSugar with FutureAwaits with DefaultAwaitTimeout {

  val testEori = "GB1234567890"

  class SubscriptionServiceScenario() {
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    val appConfig = new AppConfig(configuration, env)
    val mockHttp = mock[HttpClient]
    val mockMetricsReporterService = mock[MetricsReporterService]
    when(mockMetricsReporterService.withResponseTimeLogging(any())(any())(any()))
      .thenAnswer((i: InvocationOnMock) => {i.getArgument[Future[JsString]](1)})
    val service = new SubscriptionInfoService(appConfig, mockHttp, mockMetricsReporterService)
    implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
    implicit val hc: HeaderCarrier = HeaderCarrier()

    def mdgResponse(value: JsValue) = MdgSub09DataModel.sub09Reads.reads(value).get
  }

  "getSubscriberInformation" should {

    "return None when the timestamp is not available" in new SubscriptionServiceScenario {
      val response = mdgResponse(Sub09Response.withEmailNoTimestamp(testEori))
      when(mockHttp.GET[MdgSub09DataModel](any())(any(), any(), any())).thenReturn(Future.successful(response))

      await(service.getSubscriberInformation(testEori)) mustBe None
    }

    "return Some, when the timestamp is available" in new SubscriptionServiceScenario {
      val response = mdgResponse(Sub09Response.withEmailAndTimestamp(testEori))
      when(mockHttp.GET[MdgSub09DataModel](any())(any(), any(), any())).thenReturn(Future.successful(response))

      await(service.getSubscriberInformation(testEori)) mustBe Some(MdgSub09DataModel(Some("mickey.mouse@disneyland.com"), Some("2019-09-06T12:30:59Z")))
    }

    "return None when the email is not available" in new SubscriptionServiceScenario {
      val response = mdgResponse(Sub09Response.noEmailNoTimestamp(testEori))
      when(mockHttp.GET[MdgSub09DataModel](any())(any(), any(), any())).thenReturn(Future.successful(response))

      await(service.getSubscriberInformation(testEori)) mustBe None
    }

    "log response time metric" in new SubscriptionServiceScenario {
      val response = mdgResponse(Sub09Response.withEmailAndTimestamp(testEori))
      when(mockHttp.GET[MdgSub09DataModel](any())(any(), any(), any())).thenReturn(Future.successful(response))

      await(service.getSubscriberInformation(testEori))
      verify(mockMetricsReporterService).withResponseTimeLogging(ArgumentMatchers.eq("mdg.get.company-information"))(any())(any())
    }

    "propagate ServiceUnavailableException" in new SubscriptionServiceScenario {
      when(mockHttp.GET[MdgSub09DataModel](any())(any(),any(),any())).thenReturn(Future.failed(new ServiceUnavailableException("Boom")))
      assertThrows[ServiceUnavailableException](await(service.getSubscriberInformation(testEori)))
    }
  }
}
