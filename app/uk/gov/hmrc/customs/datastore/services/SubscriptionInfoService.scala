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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import javax.inject.Inject
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.Eori
import uk.gov.hmrc.customs.datastore.domain.onwire.MdgSub09DataModel
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class SubscriptionInfoService @Inject()(appConfig: AppConfig, http: HttpClient, metricsReporter: MetricsReporterService) {
  val log: LoggerLike = Logger(this.getClass)

  def getSubscriberInformation(eori: Eori)(implicit hc: HeaderCarrier): Future[Option[MdgSub09DataModel]] = {
    val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z").withZone(ZoneId.systemDefault())
    val localDate = LocalDateTime.now().format(dateFormat)

    //TODO Do we have to add these headers all the time, will not Customs-financials-api forward them to us???
    val headers = Seq(("Date" -> localDate),
      ("X-Correlation-ID" -> java.util.UUID.randomUUID().toString),
      ("X-Forwarded-Host" -> "MDTP"),
      ("Accept" -> "application/json"))

    val hcWithExtraHeaders: HeaderCarrier = hc.copy(authorization = Some(Authorization(appConfig.bearerToken)), extraHeaders = hc.extraHeaders ++ headers)

    val acknowledgementReference = Random.alphanumeric.take(32).mkString
    val uri = s"${appConfig.companyInformationUrl}?regime=CDS&acknowledgementReference=$acknowledgementReference&EORI=$eori"

    metricsReporter.withResponseTimeLogging("mdg.get.company-information") {
      http.GET[MdgSub09DataModel](uri)(implicitly, hcWithExtraHeaders, implicitly)
        .transform(
          s =>  if (s.verifiedTimestamp.isDefined) Some(s) else None,
          f => {log.error(s"Getting Subscriber Information failed with: ${f.getMessage}", f); f}
        )
    }
  }
}
