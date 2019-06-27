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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatest.Pending
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.words.MatcherWords
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.{POST, contentAsString, _}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{GraphQL, TraderDataSchema}
import uk.gov.hmrc.customs.datastore.services.{EoriStore, MongoSpecSupport, ServerTokenAuthorization}

import scala.concurrent.Future


class GraphQLControllerSpec extends PlaySpec with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with MockitoSugar with MatcherWords with ScalaFutures {

  val endPoint = "/graphql"
  val testEmail:EmailAddress = "bob@mail.com"
  val testEori = "122334454"
  val testValidFrom = "20180101"
  val testValidUntil = "20200101"

  class GraphQLScenario() {
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    val mockEoriStore = mock[EoriStore]
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    val appConfig = new AppConfig(configuration, env)
    val authConnector = new ServerTokenAuthorization(appConfig)

    val schema = new TraderDataSchema(mockEoriStore)
    val graphQL = new GraphQL(schema)
    val controller = new GraphQLController(authConnector, graphQL)
    val authorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json", "Authorization" -> "secret-token")
  }

  "GraphQLController" should {
    "return unauthorised exception when auth token is not present" in new GraphQLScenario() {
      val eoriNumber:Eori = "GB12345678"
      val query = s"""{ "query": "query { findEmail( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""
      val unauthorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json").withBody(query)
      val respone = controller.graphqlBody.apply(unauthorizedRequest)
      status(respone) mustBe UNAUTHORIZED
    }

    "Return trader's email for a given Eori" in new GraphQLScenario() {
      val eoriNumber:Eori = "GB12345678"
      val emailAddress = "abc@goodmail.com"
      val traderData = TraderData(
        Seq(EoriPeriod(eoriNumber, Some("2001-01-20T00:00:00Z"), None)),
        Option(NotificationEmail(Option(emailAddress))))
      when(mockEoriStore.getTraderData(any())).thenReturn(Future.successful(Option(traderData)) )
      val query = s"""{ "query": "query { findEmail( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""
      val request = authorizedRequest.withBody(query)
      val result = contentAsString(controller.graphqlBody.apply(request))
      result must include("data")
      result mustNot include("errors")

      val maybeEmailAddress= Json.parse(result).as[JsObject] \\ "address"
      maybeEmailAddress.head mustBe JsString(emailAddress)
    }

    "Insert new trader into our database" in new GraphQLScenario() {
      val eoriNumber:Eori = "GB12345678"
      val emailAddress = "abc@goodmail.com"
      when(mockEoriStore.rosmInsert(any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {addTrader(eori:\\"$eoriNumber\\" notificationEmail:\\"$emailAddress\\")}" }"""
      val request = authorizedRequest.withBody(query)
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      result mustNot include("errors")
    }

  }

}
