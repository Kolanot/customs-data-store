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

import java.time.LocalDate

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import play.api.libs.json.{JsObject, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.domain.onwire._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}


class EoriHistoryServiceSpec extends FlatSpec with MustMatchers with MockitoSugar with DefaultAwaitTimeout with FutureAwaits {

  protected def generateResponse(eoris:Seq[Eori]):HistoricEoriResponse = {
    HistoricEoriResponse(
      GetEORIHistoryResponse(
        ResponseCommon("OK", LocalDate.now().toString),
        ResponseDetail(generateEoriHistory(eoris))
      )
    )
  }

  def generateEoriHistory(allEoris:Seq[Eori]):Seq[EORIHistory] = {
    val dateCalculator = (years:Int) => "19XX-03-20T19:30:51Z".replaceAll("XX", (85 + allEoris.size - years).toString )
    @tailrec
    def calcHistory(eoris:Seq[Eori], histories:Seq[EORIHistory]):Seq[EORIHistory] = {
      val eori = eoris.head
      eoris.size match {
        case 1 =>
          EORIHistory(eori, Some(dateCalculator(eoris.size)),None) +: histories
        case _ =>
          val current = EORIHistory(eori, Some(dateCalculator(eoris.size)),Some(dateCalculator(eoris.size-1)))
          calcHistory(eoris.tail,current +: histories )
      }
    }
    calcHistory(allEoris, Seq.empty[EORIHistory])
  }

  class ETMPScenario() {
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    FeatureSwitch.MdgRequest.enable()

    val appConfig = new AppConfig(configuration,env)
    val mockHttp = mock[HttpClient]
    val service = new EoriHistoryService(appConfig, mockHttp)
    implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
    implicit val implicitHeaderCarrier: HeaderCarrier = HeaderCarrier(
      authorization = Option(Authorization("myAwesomeCrypto")),
      otherHeaders = List(("X-very-important", "foo"))
    )

    val someEori = "GB553011111009"
  }

  "EoriHistoryService" should "hit the expected URL" in new ETMPScenario {
    val actualURL: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    when(mockHttp.GET[HistoricEoriResponse](actualURL.capture())(any(), any(), any()))
      .thenReturn(Future.successful(generateResponse(List(someEori))))

    await(service.getHistory(someEori))

    actualURL.getValue mustBe appConfig.eoriHistoryUrl + someEori
  }

  "EoriHistoryService" should "return a list of EoriPeriod entries" in new ETMPScenario {
    val jsonResponse = s"""{
                         |  "getEORIHistoryResponse": {
                         |    "responseCommon": {
                         |      "status": "OK",
                         |      "processingDate": "2019-07-26T10:21:13Z"
                         |    },
                         |    "responseDetail": {
                         |      "EORIHistory": [
                         |        {
                         |          "EORI": "$someEori",
                         |          "validFrom": "2019-07-24"
                         |        },
                         |        {
                         |          "EORI": "GB550011111009",
                         |          "validFrom": "2009-05-16",
                         |          "validTo": "2019-07-23"
                         |        },
                         |        {
                         |          "EORI": "GB551011111009",
                         |          "validFrom": "2019-07-24",
                         |          "validTo": "2019-07-23"
                         |        },
                         |        {
                         |          "EORI": "GB552011111009",
                         |          "validFrom": "2019-07-24",
                         |          "validTo": "2019-07-23"
                         |        }
                         |      ]
                         |    }
                         |  }
                         |}""".stripMargin
    when(mockHttp.GET[HistoricEoriResponse](any())(any(),any(),any()))
      .thenReturn(Future.successful(Json.fromJson[HistoricEoriResponse](Json.parse(jsonResponse).as[JsObject]).get))

    private val response = await(service.getHistory(someEori))

    response mustBe List(
      EoriPeriod("GB553011111009",Some("2019-07-24"),None),
      EoriPeriod("GB550011111009",Some("2009-05-16"),Some("2019-07-23")),
      EoriPeriod("GB551011111009",Some("2019-07-24"),Some("2019-07-23")),
      EoriPeriod("GB552011111009",Some("2019-07-24"),Some("2019-07-23"))
    )
  }

  "EoriHistoryService" should "return an empty list when feature mdg-request is disabled" in new ETMPScenario {
    FeatureSwitch.MdgRequest.disable()
    when(mockHttp.GET[HistoricEoriResponse](any())(any(),any(),any())).thenReturn(Future.successful(generateResponse(List(someEori))))

    private val response = await(service.getHistory(someEori))
    response mustBe List()
  }

  "EoriHistoryService" should "propagate the HeaderCarrier through to the HTTP request, overwriting the Auth header" in new ETMPScenario {
    val actualHeaderCarrier: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
    when(mockHttp.GET[HistoricEoriResponse](any())(any(), actualHeaderCarrier.capture(), any()))
      .thenReturn(Future.successful(generateResponse(List(someEori))))

    await(service.getHistory(someEori))

    private val expectedHeaderCarrier = implicitHeaderCarrier.copy(authorization = Some(Authorization("Bearer secret-token")))
    actualHeaderCarrier.getValue mustBe expectedHeaderCarrier
  }

}
