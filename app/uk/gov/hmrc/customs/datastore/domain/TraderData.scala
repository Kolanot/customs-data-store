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

package uk.gov.hmrc.customs.datastore.domain

import play.api.libs.json.Json

case class EoriPeriod(eori: Eori,
                      validFrom: Option[String],
                      validUntil: Option[String])

case class Email(address: EmailAddress,
                 isValidated: Boolean)  //TODO Add some field to store default email


case class TraderData(credentialId: Option[CredentialId],
                      eoriHistory: Seq[EoriPeriod],
                      emails:Seq[Email])

object TraderData {
  implicit val eoriPeriodFormat = Json.format[EoriPeriod]
  implicit val emailFormat = Json.format[Email]
  implicit val traderDataFormat = Json.format[TraderData]
}