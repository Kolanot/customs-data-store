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

package uk.gov.hmrc.customs.datastore.domain.onwire

import org.scalatest.{MustMatchers, WordSpec}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.customs.datastore.domain.Eori

class MdgSub09DataModelSpec extends WordSpec with MustMatchers {

  val EORI1 = "GB0000000001"

  "The DataModel" should {
    "parse message with email and a timestamp" in {
      val sub09Response = Sub09Response.withEmailAndTimestamp(EORI1)
      val result = MdgSub09DataModel.sub09Reads.reads(sub09Response).get
      result mustBe MdgSub09DataModel(Some("mickey.mouse@disneyland.com"), Some("2019-09-06T12:30:59Z"))
    }

    "parse message with email and no timestamp" in {
      val sub09Response = Sub09Response.withEmailNoTimestamp(EORI1)
      val result = MdgSub09DataModel.sub09Reads.reads(sub09Response).get
      result mustBe MdgSub09DataModel(Some("mickey.mouse@disneyland.com"), None)
    }

    "parse message no email and no timestamp" in {
      val sub09Response = Sub09Response.noEmailNoTimestamp(EORI1)
      val result = MdgSub09DataModel.sub09Reads.reads(sub09Response).get
      result mustBe MdgSub09DataModel(None, None)
    }
  }

}


object Sub09Response {

  private val timeStampKey = "--THE-TIMESTAMP--"
  private val emailKey = "--THE-EMAIL--"
  private val eoriKey = "--THE-EORI-HERE--"

  def withEmailAndTimestamp(eori: Eori): JsValue = {
    val response = sub09Response(eori)
      .replace(emailKey, """ "emailAddress": "mickey.mouse@disneyland.com", """)
      .replace(timeStampKey,""" "emailVerificationTimestamp": "2019-09-06T12:30:59Z",""")
    Json.parse(response)
  }

  //TODO You can remove this once ETMP updates their api (The timestamp currently is not part of the response, but it will be later
  def withEmailNoTimestamp(eori: Eori): JsValue = {
    val response = sub09Response(eori)
      .replace(emailKey, """ "emailAddress": "mickey.mouse@disneyland.com", """)
      .replace(timeStampKey, "")
    Json.parse(response)
  }

  def noEmailNoTimestamp(eori: Eori): JsValue = {
    val response = sub09Response(eori)
      .replace(emailKey, "")
      .replace(timeStampKey, "")
    Json.parse(response)
  }

  protected def sub09Response(eori: Eori): String =
    s"""
       |{
       |  "subscriptionDisplayResponse": {
       |    "responseCommon": {
       |      "status": "OK",
       |      "statusText": "Optional status text from ETMP",
       |      "processingDate": "2016-08-17T19:33:47Z",
       |      "returnParameters": [
       |        {
       |          "paramName": "POSITION",
       |          "paramValue": "LINK"
       |        }
       |      ]
       |    },
       |    "responseDetail": {
       |      "EORINo": "$eoriKey",
       |      "EORIStartDate": "1999-01-01",
       |      "EORIEndDate": "2020-01-01",
       |      "CDSFullName": "Mickey Mouse",
       |      "CDSEstablishmentAddress": {
       |        "streetAndNumber": "86 Mysore Road",
       |        "city": "London",
       |        "postalCode": "SW11 5RZ",
       |        "countryCode": "GB"
       |      },
       |      "establishmentInTheCustomsTerritoryOfTheUnion": "0",
       |      "typeOfLegalEntity": "0001",
       |      "contactInformation": {
       |        "personOfContact": "Minnie Mouse",
       |        "streetAndNumber": "2nd floor, Alexander House",
       |        "city": "Southend-on-sea",
       |        "postalCode": "SS99 1AA",
       |        "countryCode": "GB",
       |        "telephoneNumber": "01702215001",
       |        $timeStampKey
       |        $emailKey
       |        "faxNumber": "01702215002"
       |      },
       |      "VATIDs": [
       |        {
       |          "countryCode": "GB",
       |          "VATID": "12164568990"
       |        }
       |      ],
       |      "thirdCountryUniqueIdentificationNumber": [
       |        "321",
       |        "222"
       |      ],
       |      "consentToDisclosureOfPersonalData": "1",
       |      "shortName": "Mick",
       |      "dateOfEstablishment": "1963-04-01",
       |      "typeOfPerson": "1",
       |      "principalEconomicActivity": "2000",
       |      "ETMP_Master_Indicator": true
       |    }
       |  }
       |}
    """.stripMargin.replace(eoriKey, eori)

}
