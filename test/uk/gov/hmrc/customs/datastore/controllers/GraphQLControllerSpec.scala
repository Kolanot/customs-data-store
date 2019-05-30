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

import org.mockito.ArgumentMatchers.{eq => is, _}
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.words.MatcherWords
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.{POST, contentAsString}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{GraphQL, InputEmail, TraderDataSchema}
import uk.gov.hmrc.customs.datastore.services.{EoriStore, MongoSpecSupport}

import scala.concurrent.Future


class GraphQLControllerSpec extends PlaySpec with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with MockitoSugar with MatcherWords{

  val endPoint = "/graphql"
  val internalId = "12345678"
  val testEmail:EmailAddress = "bob@mail.com"

  class GraphQLScenario() {
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    val mockEoriStore = mock[EoriStore]
    val schema = new TraderDataSchema(mockEoriStore)
    val graphQL = new GraphQL(schema)
    val controller = new GraphQLController(graphQL)
  }

  "GraphQLController" should {
    "Return trader's email for a given Eori" in new GraphQLScenario() {
      val eoriNumber:Eori = "GB12345678"
      val emailAddress = "abc@goodmail.com"
      val traderData = TraderData(
        Some("1234"),
        Seq(EoriPeriod(eoriNumber, Some("2001-01-20T00:00:00Z"), None)),
        Option(NotificationEmail(emailAddress, true)))
      when(mockEoriStore.getTraderData(any())).thenReturn(Future.successful(Option(traderData)) )
      val query = s"""{ "query": "query { findEmail( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""
      val request = FakeRequest(POST, "/graphql").withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))
      result must include("data")
      result mustNot include("errors")

      val maybeCredentialId = Json.parse(result).as[JsObject] \\ "address"
      maybeCredentialId.head mustBe JsString(emailAddress)
    }

    "Insert new trader into our database" in new GraphQLScenario() {
      val credentialId:InternalId = "1111111"
      val eoriNumber:Eori = "GB12345678"
      val emailAddress = "abc@goodmail.com"
      when(mockEoriStore.rosmInsert(any(),any(),any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {addTrader(credentialId:\\"$credentialId\\" eori:\\"$eoriNumber\\" notificationEmail:\\"$emailAddress\\" isValidated:true )}" }"""
      val request = FakeRequest(POST, "/graphql").withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      result mustNot include("errors")
    }

  }

  "Upsert byInternalId" should {
    "work without any options given" in new GraphQLScenario {
      when(mockEoriStore.upsertByInternalId(any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byInternalId(internalId:\\"$internalId\\" )}" }"""
      val request = FakeRequest(POST, endPoint).withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      verify(mockEoriStore).upsertByInternalId(is(internalId),is(None))
    }

    "work with empty notificationEmail body" in new GraphQLScenario {
      when(mockEoriStore.upsertByInternalId(any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byInternalId(internalId:\\"$internalId\\" notificationEmail:{})}" }"""
      val request = FakeRequest(POST, endPoint).withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      verify(mockEoriStore).upsertByInternalId(is(internalId),is(Some(InputEmail(None,None))))
    }

    "work with notificationEmail body with address" in new GraphQLScenario {
      when(mockEoriStore.upsertByInternalId(any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byInternalId(internalId:\\"$internalId\\" notificationEmail:{address:\\"$testEmail\\"})}" }"""
      val request = FakeRequest(POST, endPoint).withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      verify(mockEoriStore).upsertByInternalId(is(internalId),is(Some(InputEmail(Some(testEmail),None))))
    }

    "work with notificationEmail body with isValidated" in new GraphQLScenario {
      when(mockEoriStore.upsertByInternalId(any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byInternalId(internalId:\\"$internalId\\" notificationEmail:{isValidated:true})}" }"""
      val request = FakeRequest(POST, endPoint).withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      verify(mockEoriStore).upsertByInternalId(is(internalId),is(Some(InputEmail(None, Some(true)))))
    }

    "work with notificationEmail body with all fields" in new GraphQLScenario {
      when(mockEoriStore.upsertByInternalId(any(),any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byInternalId(internalId:\\"$internalId\\" notificationEmail:{address:\\"$testEmail\\" isValidated:true})}" }"""
      val request = FakeRequest(POST, endPoint).withHeaders(("Content-Type", "application/json")).withBody(Json.parse(query))
      val result = contentAsString(controller.graphqlBody.apply(request))

      result must include("data")
      verify(mockEoriStore).upsertByInternalId(is(internalId),is(Some(InputEmail(Some(testEmail),Some(true)))))
    }

  }


}
