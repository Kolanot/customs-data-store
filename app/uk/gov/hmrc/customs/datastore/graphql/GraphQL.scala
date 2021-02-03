/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import sangria.schema.{ObjectType, fields}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton()
class GraphQL @Inject()(traderSchema: TraderDataSchema) {

  def schema()(implicit hc: HeaderCarrier) = sangria.schema.Schema(
    query = ObjectType("Query",
      fields(
        traderSchema.Queries: _*
      )
    ),
    mutation = Some(
      ObjectType("Mutation",
        fields(
          traderSchema.Mutations: _*
        )
      )
    )
  )
}