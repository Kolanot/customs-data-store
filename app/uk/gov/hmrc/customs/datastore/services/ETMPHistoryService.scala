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
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.HistoricEoriResponse._
import uk.gov.hmrc.customs.datastore.domain.{Eori, EoriHistory, HistoricEoriResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ETMPHistoryService @Inject()(appConfig:AppConfig, http: HttpClient) {

  def getHistory(eori: Eori)(implicit hc: HeaderCarrier): Future[Seq[EoriHistory]] = {
    http.GET[HistoricEoriResponse](s"${appConfig.eoriHistoryUrl}/$eori")
      .map { response =>
        response.getEORIHistoryResponse.responseDetail.EORIHistory.toSeq.map {
          eori => EoriHistory(eori.EORI, eori.validFrom, eori.validUntil)
        }
      }
  }

}
