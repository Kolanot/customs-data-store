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

class SubscriptionInfoService @Inject()(appConfig: AppConfig, http: HttpClient) {
  val log: LoggerLike = Logger(this.getClass)

  def getHistory(eori: Eori)(implicit hc: HeaderCarrier): Future[Option[MdgSub09DataModel]] = {
    if(FeatureSwitch.MdgRequest.isEnabled()) {
      val hci: HeaderCarrier = hc.copy(authorization = Some(Authorization(appConfig.bearerToken)))
      val acknowledgementReference = Random.alphanumeric.take(32)
      val uri = s"${appConfig.companyInformationUrl}regime=CDS&acknowledgementReference=$acknowledgementReference&EORI=$eori"
      http.GET[MdgSub09DataModel](uri)(implicitly, hci, implicitly).map{m => m.verifiedTimestamp match {
        case Some(_) => Some(m)
        case None => None
      }}
    } else {
      Future.successful(None)
    }
  }
}
