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

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.HistoricEoriResponse._
import uk.gov.hmrc.customs.datastore.domain.{Eori, EoriPeriod, HistoricEoriResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

//Call MDG to retrieve the latest historic Eori's for a given Eori from ETMP
class ETMPHistoryService @Inject()(appConfig:AppConfig, http: HttpClient) {

  def getHistory(eori: Eori)(implicit hc: HeaderCarrier, reads: HttpReads[HistoricEoriResponse]): Future[Seq[EoriPeriod]] = {
    val hci: HeaderCarrier = hc.withExtraHeaders("Authorization" -> s"Bearer ${appConfig.bearerToken}")
    http.GET[HistoricEoriResponse](s"${appConfig.eoriHistoryUrl}$eori")(reads, hci, implicitly[ExecutionContext])
      .map { response =>
        response.getEORIHistoryResponse.responseDetail.EORIHistory.map {
          history => EoriPeriod(history.EORI, history.validFrom, history.validTo)
        }
      }
  }

  def testSub21(eori: String)(implicit  hc:HeaderCarrier, reads: HttpReads[HttpResponse], ec: ExecutionContext):Future[JsValue] = {

    val hci: HeaderCarrier = hc.withExtraHeaders("Authorization" -> s"Bearer ${appConfig.bearerToken}")
    val mdgUrl =appConfig.eoriHistoryUrl    ///subscriptions/geteorihistory/v1"
    Logger.info(s"This is a test MDG endpoint : $mdgUrl")

    Logger.info("MDG request headers: "+hc.headers)
    //val reads = Json.reads[JsObject]
    http.GET[HttpResponse](mdgUrl, Seq("eori" -> eori))(reads, hci, ec).map(a => Json.parse(a.body))

  }

}
