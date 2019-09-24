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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => is, _}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.words.MatcherWords
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.{POST, contentAsString, _}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{GraphQL, TraderDataSchema}
import uk.gov.hmrc.customs.datastore.services.{EoriHistoryService, EoriStore, MongoSpecSupport, ServerTokenAuthorization}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.RequestId
import uk.gov.hmrc.mongo.MongoConnector

import scala.collection.JavaConverters._
import scala.concurrent.Future


class GraphQLControllerSpec extends PlaySpec with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with MockitoSugar with MatcherWords with ScalaFutures {

  val endPoint = "/graphql"
  val testEmail: EmailAddress = "bob@mail.com"
  val testEori = "122334454"
  val testValidFrom = "20180101"
  val testValidUntil = "20200101"
  val testTimestamp = "timestamp"

  class GraphQLScenario() {

    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    val mockHistoryService = mock[EoriHistoryService]
    when(mockHistoryService.getHistory(is(testEori))(any(), any()))
      .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("1987.03.20"), None))))
    val mockEoriStore = mock[EoriStore]
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    val appConfig = new AppConfig(configuration, env)
    val authConnector = new ServerTokenAuthorization(appConfig)

    val schema = new TraderDataSchema(mockEoriStore, mockHistoryService)
    val graphQL = new GraphQL(schema)
    val controller = new GraphQLController(authConnector, graphQL)
    val authorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer secret-token")
  }

  "GraphQLController" should {
    "return unauthorised exception when auth token is not present" in new GraphQLScenario() {
      val eoriNumber: Eori = "GB12345678"
      val query = s"""{ "query": "query { byEori( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""
      val unauthorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json").withBody(Json.parse(query))
      val response = controller.handleQuery.apply(unauthorizedRequest)
      status(response) mustBe UNAUTHORIZED
    }

    "Return trader's email from the cache, for a given Eori" in new GraphQLScenario() {
      val eoriNumber: Eori = "GB12345678"
      val emailAddress = "abc@goodmail.com"
      val traderData = TraderData(
        Seq(EoriPeriod(eoriNumber, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(Some(emailAddress), None)))
      when(mockEoriStore.findByEori(is(eoriNumber))).thenReturn(Future.successful(Some(traderData)))
      val query = s"""{ "query": "query { byEori( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""
      val request = authorizedRequest.withBody(Json.parse(query))
      val result = contentAsString(controller.handleQuery.apply(request))
      result must include("data")
      result mustNot include("errors")

      val maybeEmailAddress = Json.parse(result).as[JsObject] \\ "address"
      maybeEmailAddress.head mustBe JsString(emailAddress)
    }

    "Load Eori history from MDG and store in the cache" in new GraphQLScenario() {
      val eoriNumber: Eori = "GB12345678"
      val emailAddress = "abc@goodmail.com"
      val traderData = TraderData(
        Seq(EoriPeriod(eoriNumber, None, None)), // TODO improve this model
        Some(NotificationEmail(Some(emailAddress), None)))

      val actualHeaderCarrier: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      val historicEoris = Seq(
        EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None),
        EoriPeriod("GB222222", Some("2002-01-20T00:00:00Z"), Some("2001-01-20T00:00:00Z")),
        EoriPeriod("GB111111", Some("2001-01-20T00:00:00Z"), Some("1999-01-20T00:00:00Z"))
      )
      when(mockHistoryService.getHistory(is(eoriNumber))(actualHeaderCarrier.capture(), any()))
        .thenReturn(Future.successful(historicEoris))

      when(mockEoriStore.findByEori(is(eoriNumber)))
        .thenReturn(Future.successful(Some(traderData)), Future.successful((Some(TraderData(historicEoris, None)))))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))

      val query = s"""{ "query": "query { byEori( eori: \\"$eoriNumber\\") { eoriHistory { eori }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"
      val request = authorizedRequest.withBody(Json.parse(query))
        .withHeaders("X-Request-ID" -> queryRequestId)

      val result = contentAsString(controller.handleQuery.apply(request))
      result must include("data")
      result mustNot include("errors")

      val maybeEoris = Json.parse(result).as[JsObject] \\ "eori"
      maybeEoris mustBe List(JsString(testEori), JsString("GB222222"), JsString("GB111111"))

      verify(mockEoriStore).updateHistoricEoris(historicEoris)

      actualHeaderCarrier.getAllValues.asScala.map(_.requestId) mustBe List(Some(RequestId(queryRequestId)))
    }

    "Insert by Eori" in new GraphQLScenario() {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\" validFrom:\\"$testValidFrom\\" validUntil:\\"$testValidUntil\\"}, notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
      val request = authorizedRequest.withBody(Json.parse(query))
      val result = contentAsString(controller.handleQuery.apply(request))

      result must include("data")
      result mustNot include("errors")
      val eoriPeriod = EoriPeriod(testEori, Some(testValidFrom), Some(testValidUntil))
      val email = NotificationEmail(Some(testEmail), Some(testTimestamp))
      verify(mockEoriStore).upsertByEori(eoriPeriod, Some(email))
    }

    "Insert an EORI with no email address" in new GraphQLScenario() {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\" validFrom:\\"$testValidFrom\\" validUntil:\\"$testValidUntil\\"} )}" }"""
      val request = authorizedRequest.withBody(Json.parse(query))
      val result = contentAsString(controller.handleQuery.apply(request))

      result must include("data")
      result mustNot include("errors")
      val eoriPeriod = EoriPeriod(testEori, Some(testValidFrom), Some(testValidUntil))
      verify(mockEoriStore).upsertByEori(eoriPeriod, None)
    }

    "Return an error response when queried without an EORI" in new GraphQLScenario() {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byEori(notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
      val request = authorizedRequest.withBody(Json.parse(query))

      val response = controller.handleQuery.apply(request)

      status(response) mustBe BAD_REQUEST
      val result = contentAsString(response)
      result must include("data")
      result must include("errors")
      result must include("not provided")

      verify(mockHistoryService, never()).getHistory(any())(any(), any())
      verify(mockEoriStore, never()).upsertByEori(any(), any())
      verify(mockEoriStore, never()).updateHistoricEoris(any())
    }
  }

  "Integration tests" should {
    "Add an email address to a trader's record, and then retrieve historic Eoris from the cache" in {
      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      val as = ActorSystem("EoriStoreAs")
      val materializer = ActorMaterializer()(as)

      val actualHeaderCarrier: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      val historyService = mock[EoriHistoryService]
      val historicEoris = Seq(
        EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None),
        EoriPeriod("GB222222", Some("2002-01-20T00:00:00Z"), Some("2001-01-20T00:00:00Z")),
        EoriPeriod("GB111111", Some("2001-01-20T00:00:00Z"), Some("1999-01-20T00:00:00Z"))
      )
      when(historyService.getHistory(is(testEori))(actualHeaderCarrier.capture(), any()))
        .thenReturn(Future.successful(historicEoris))

      val env = Environment.simple()
      val configuration = Configuration.load(env)
      val appConfig = new AppConfig(configuration, env)
      val authConnector = new ServerTokenAuthorization(appConfig)

      val eoriStore = new EoriStore(new ReactiveMongoComponent {
        override def mongoConnector: MongoConnector = mongoConnectorForTest
      }, appConfig)

      val schema = new TraderDataSchema(eoriStore, historyService)
      val graphQL = new GraphQL(schema)
      val controller = new GraphQLController(authConnector, graphQL)
      val authorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer secret-token")

      val mutation = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\"}, notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
      val mutationRequestId = "mutate-this"
      val mutationRequest = authorizedRequest.withBody(Json.parse(mutation))
        .withHeaders("X-Request-ID" -> mutationRequestId)

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori validFrom validUntil},  notificationEmail { address, timestamp }  } }"}"""
      val queryRequest = authorizedRequest.withBody(Json.parse(query))
        .withHeaders("X-Request-ID" -> "can-i-haz-eori")

      val result = await(for {
        _ <- controller.handleQuery.apply(mutationRequest)
        queryResult <-  controller.handleQuery.apply(queryRequest)
        byteString <- queryResult.body.consumeData(materializer)
        json <- Future.successful(Json.parse(byteString.utf8String))
      } yield json)

      (result \\ "eori").map(a => a.as[JsString].value) mustBe List(testEori, "GB222222" ,"GB111111")
      (result \\ "address").head.as[JsString].value mustBe testEmail
      actualHeaderCarrier.getAllValues.asScala.map(_.requestId) mustBe List(Some(RequestId(mutationRequestId)))
    }
  }
}
