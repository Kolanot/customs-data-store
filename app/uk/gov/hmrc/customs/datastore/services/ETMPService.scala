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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import javax.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.{Eori, EoriPeriod, HistoricEoriResponse}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ETMPService @Inject()(appConfig: AppConfig, http: HttpClient) {

  val log: LoggerLike = Logger(this.getClass)

  def getHistory(eori: Eori)(implicit hc: HeaderCarrier, reads: HttpReads[HistoricEoriResponse]): Future[Seq[EoriPeriod]] = {
    if(FeatureSwitch.MdgRequest.isEnabled()) {
      val hci: HeaderCarrier = hc.copy(authorization = Some(Authorization(appConfig.bearerToken)))
      http.GET[HistoricEoriResponse](s"${appConfig.eoriHistoryUrl}$eori")(reads, hci, implicitly)
        .map { response =>
          response.getEORIHistoryResponse.responseDetail.EORIHistory.map {
            history => EoriPeriod(history.EORI, history.validFrom, history.validTo)
          }
        }
    } else {
      Future.successful(Nil)
    }
  }

  // TODO: implement this
  def getCompanyInformation(eori: Eori)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (FeatureSwitch.GetCompanyInfoFromMdg.isEnabled()) {} else {}
    Future.successful()
  }

  def testSub21(eori: String)(implicit hc: HeaderCarrier, reads: HttpReads[HttpResponse], ec: ExecutionContext): Future[JsValue] = {

    val hci: HeaderCarrier = hc
    val mdgUrl = appConfig.eoriHistoryUrl + eori
    log.info(s"This is a test MDG endpoint : $mdgUrl")

    log.info("MDG request headers: " + hci.headers)
    http.GET[HttpResponse](mdgUrl)(reads, hci, ec).map(a => Json.parse(a.body))

  }

  def testSub09(eori: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z").withZone(ZoneId.systemDefault())
    val localDate = LocalDateTime.now().format(dateFormat)

    val headers = Seq(("Date"->localDate),
      ("X-Correlation-ID"->java.util.UUID.randomUUID().toString),
      ("X-Forwarded-Host"->"MDTP"),
      ("Accept"->"application/json"))

    val hcWithExtraHeaders: HeaderCarrier = hc.copy(authorization = Some(Authorization(s"Bearer ${appConfig.bearerToken}")), extraHeaders = hc.extraHeaders ++ headers)

    log.info("MDG request headers: " + hcWithExtraHeaders)

    val queryParams = Seq(("regime"->"CDS"),("acknowledgementReference"->"21a2b17559e64b14be257a112a7d9e8e"),("EORI"->eori))

    val mdgUrl = appConfig.eoriHistoryUrl + "/subscriptions/subscriptiondisplay/v1"

    log.info("MDG sub09 URL: " + mdgUrl)

    http.GET[HttpResponse](mdgUrl, queryParams)(implicitly, hcWithExtraHeaders, implicitly)
      .map{ a => log.info(a.body)
        Json.parse(a.body)}
  }

}
