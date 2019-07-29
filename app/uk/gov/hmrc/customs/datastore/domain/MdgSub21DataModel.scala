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

/*
 * on the wire format data models for the MDG Sub 21 request, which returns historic Eoris for a given Eori
 */

case class HistoricEoriResponse(getEORIHistoryResponse: GetEORIHistoryResponse)

case class GetEORIHistoryResponse(responseCommon: ResponseCommon,
                                  responseDetail: ResponseDetail)

case class ResponseCommon(status: String,
                          processingDate: String)

case class ResponseDetail(EORIHistory: Seq[EORIHistory])

case class EORIHistory(EORI: Eori,
                       validFrom: Option[String],
                       validTo: Option[String])

object HistoricEoriResponse {
  implicit val eoriHistoryFormat = Json.format[EORIHistory]
  implicit val responseDetailFormat = Json.format[ResponseDetail]
  implicit val responseCommonFormat = Json.format[ResponseCommon]
  implicit val getEORIHistoryResponseFormat = Json.format[GetEORIHistoryResponse]
  implicit val historicEoriResponseFormat = Json.format[HistoricEoriResponse]
}