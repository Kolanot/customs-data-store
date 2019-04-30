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
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.customs.datastore.domain.{EoriHistory, EoriHistoryResponse}
import uk.gov.hmrc.customs.datastore.services.EoriStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HistoricEoriControllerSpec extends WordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  implicit val mat = app.materializer

  val fakeRequest = FakeRequest("GET", "/")

  "GET /" should {

    "return 200" in {
      val eori = "GB1234567890"
      val mockEoriStore = mock[EoriStore]
      when(mockEoriStore.getEori(ArgumentMatchers.eq(eori)))
        .thenReturn(Future.successful(Some(EoriHistoryResponse(Seq()))))

      val controller = new HistoricEoriController(mockEoriStore)
      val result = controller.getEoriHistory(eori)(fakeRequest)
      status(result) shouldBe Status.OK
    }

  }

  "getEoriHistory" should {

    "return the expected eori history" in {
      val eori = "GB1234567890"
      val eoriHistory = Seq(EoriHistory(eori, None, None))

      val mockEoriStore = mock[EoriStore]
      when(mockEoriStore.getEori(ArgumentMatchers.eq(eori)))
        .thenReturn(Future.successful(Some(EoriHistoryResponse(eoriHistory))))

      val expectedResponse =
        """{
          |  "eoris" : [ {
          |    "eori" : "GB1234567890"
          |  } ]
          |}""".stripMargin

      val controller = new HistoricEoriController(mockEoriStore)
      val response = contentAsJson(call(controller.getEoriHistory(eori), fakeRequest))

      Json.prettyPrint(response) shouldBe expectedResponse
    }

    }

}