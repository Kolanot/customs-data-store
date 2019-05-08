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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}


class ETMPHistoryServiceSpec extends FlatSpec with MustMatchers with MockitoSugar with DefaultAwaitTimeout with FutureAwaits {

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

    val appConfig = new AppConfig(configuration,env)
    val mockHttp = mock[HttpClient]
    val service = new ETMPHistoryService(appConfig, mockHttp)
    implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext
    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  it should "work" in new ETMPScenario {
    val EORI1 = "GB0000000001"
    when(mockHttp.GET[HistoricEoriResponse](any())(any(),any(),any())).thenReturn(Future.successful(generateResponse(List(EORI1))))

    val  response = await(service.getHistory(EORI1))
    response mustBe List(EoriHistory(EORI1,Some("1985-03-20T19:30:51Z"),None))
  }



}
