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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.onwire.{MdgSub09DataModel, Sub09Response}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}


class SubscriptionInfoServiceSpec extends WordSpec with MustMatchers with MockitoSugar with FutureAwaits with DefaultAwaitTimeout {

  val testEori = "GB1234567890"

  class SubscriptionServiceScenario() {
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    val appConfig = new AppConfig(configuration, env)
    val mockHttp = mock[HttpClient]
    val service = new SubscriptionInfoService(appConfig, mockHttp)
    implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  "The Service" should {

    "Return None when the timestamp is not available" in new SubscriptionServiceScenario {
      val mdgResponse = MdgSub09DataModel.sub09Reads.reads(Sub09Response.noTimestamp(testEori)).get
      when(mockHttp.GET[MdgSub09DataModel](any())(any(), any(), any())).thenReturn(Future.successful(mdgResponse))

      val response = await(service.getHistory(testEori))
      response mustBe None
    }

    "Return Some, when the timestamp is available" in new SubscriptionServiceScenario {
      val mdgResponse = MdgSub09DataModel.sub09Reads.reads(Sub09Response.withTimestamp(testEori)).get
      when(mockHttp.GET[MdgSub09DataModel](any())(any(), any(), any())).thenReturn(Future.successful(mdgResponse))

      val response = await(service.getHistory(testEori))
      response mustBe Some(MdgSub09DataModel("mickey.mouse@disneyland.com", Some("2019-09-06T12:30:59Z")))
    }

  }
}
