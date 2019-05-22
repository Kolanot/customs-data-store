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

package uk.gov.hmrc.customs.datastore.graphql

import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.schema._
import uk.gov.hmrc.customs.datastore.domain.{Email, EoriPeriod, TraderData}
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.datastore.services.EoriStore

@Singleton
class TraderDataSchema @Inject()(eoriStore: EoriStore) {

  implicit val EoriHistoryType: ObjectType[Unit, EoriPeriod] = deriveObjectType[Unit, EoriPeriod](ObjectTypeName("EoriHistory"))
  implicit val EmailType: ObjectType[Unit, Email] = deriveObjectType[Unit, Email](ObjectTypeName("Email"))
  implicit val TraderDataType: ObjectType[Unit, TraderData] = deriveObjectType[Unit, TraderData](ObjectTypeName("TraderData"))

  val Queries: List[Field[Unit, Unit]] = List(
    Field(
      name = "trader",
      fieldType = ListType(TraderDataType),
      resolve = _ => List(TraderData(Some("1234"), Seq(EoriPeriod("123123", Some("2001-01-20T00:00:00Z"), None)),Seq.empty))
    )
  )

}
