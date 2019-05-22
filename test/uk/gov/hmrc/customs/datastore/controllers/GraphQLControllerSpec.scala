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

package uk.gov.hmrc.customs.datastore.controllers

import org.scalatest.mockito.MockitoSugar
import org.scalatest.words.MatcherWords
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.{POST, contentAsString}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.customs.datastore.graphql.{GraphQL, TraderDataSchema}


class GraphQLControllerSpec extends PlaySpec with DefaultAwaitTimeout with FutureAwaits with MockitoSugar with MatcherWords{

  class GraphQLScenario() {
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    val schema = new TraderDataSchema()
    val graphQL = new GraphQL(schema)
    val controller = new GraphQLController(graphQL)
  }

  "GraphQLController" should {
    "Return TraderData" in new GraphQLScenario() {
      val query = Json.parse("""{ "query": "query { trader { credentialId  } }"}""")
      val request = FakeRequest(POST, "/graphql").withHeaders(("Content-Type", "application/json")).withBody(query)

      val futureResult = controller.graphqlBody.apply(request)

      val result = contentAsString(futureResult)

      result must include("data")
      result mustNot include("errors")

      val maybeCredentialId = Json.parse(result).as[JsObject] \\ "credentialId"
      maybeCredentialId.head mustBe JsString("1234")
    }
  }

}