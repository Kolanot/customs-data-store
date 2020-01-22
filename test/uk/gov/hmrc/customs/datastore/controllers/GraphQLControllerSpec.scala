/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => is, _}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.words.MatcherWords
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentAsString, _}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{GraphQL, TraderDataSchema}
import uk.gov.hmrc.customs.datastore.services._
import uk.gov.hmrc.http.{HeaderCarrier, ServiceUnavailableException, Upstream5xxResponse}
import uk.gov.hmrc.http.logging.RequestId

import scala.collection.JavaConverters._
import scala.concurrent.Future


class GraphQLControllerSpec extends PlaySpec with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with MockitoSugar with MatcherWords with ScalaFutures {

  val endPoint = "/graphql"
  val testEmail: EmailAddress = "bob@mail.com"
  val testEori = "GB111111"
  val testValidFrom = "20180101"
  val testValidUntil = "20200101"
  val testTimestamp = "timestamp"

  class GraphQLScenario() {

    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    val mockHistoryService = mock[EoriHistoryService]
    when(mockHistoryService.getHistory(is(testEori))(any(), any()))
      .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("1987.03.20"), None))))
    val mockCustomerInfoService = mock[SubscriptionInfoService]
    val mockEoriStore = mock[EoriStore]
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    val appConfig = new AppConfig(configuration, env)
    val authConnector = new ServerTokenAuthorization(appConfig)
    FeatureSwitch.DataStore.enable()

    val schema = new TraderDataSchema(mockEoriStore, mockHistoryService, mockCustomerInfoService)
    val graphQL = new GraphQL(schema)
    val controller = new GraphQLController(authConnector, graphQL)
    val authorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer secret-token")
  }

  "GraphQL Queries" should {
    "return unauthorised exception when auth token is not present" in new GraphQLScenario() {
      val eoriNumber: Eori = "GB12345678"
      val query = s"""{ "query": "query { byEori( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""
      val unauthorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json").withBody(Json.parse(query))
      val response = controller.handleRequest.apply(unauthorizedRequest)
      status(response) mustBe UNAUTHORIZED
    }

    "not call the HistoricEoriService if we already have them cached" in new GraphQLScenario() {
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(Some(testEmail), None)))
      when(mockEoriStore.findByEori(is(testEori))).thenReturn(Future.successful(Some(traderData)))
      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""
      val request = authorizedRequest.withBody(Json.parse(query))
      val result = contentAsString(controller.handleRequest.apply(request))
      result mustBe s"""{"data":{"byEori":{"notificationEmail":{"address":"$testEmail"}}}}"""
    }

    "be able to request emails" in new GraphQLScenario() {
      val verifiedEmail = NotificationEmail(Some(testEmail), Some(testTimestamp))
      val traderData = TraderData(Seq(EoriPeriod(testEori, None, None)), Some(verifiedEmail))
      when(mockEoriStore.findByEori(is(testEori))).thenReturn(Future.successful(Some(traderData)))
      //when(mockCustomerInfoService.getSubscriberInformation(is(testEori))(any())).thenReturn(Future.failed(Upstream5xxResponse("ServiceUnavailable", 503, 503)))

      val query = s"""{ "query": "query { getEmail( eori: \\"$testEori\\") { address } }"}"""
      val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> "requestId")

      val result = contentAsString(controller.handleRequest()(request))
      result must include(verifiedEmail.address.get)
    }

    "call the HistoricEoriService if we don't have them cached" in new GraphQLScenario() {
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, None, None)), // TODO improve this model
        Some(NotificationEmail(Some(testEmail), None)))

      val historicEoris = Seq(
        EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None),
        EoriPeriod("GB222222", Some("2002-01-20T00:00:00Z"), Some("2001-01-20T00:00:00Z")),
        EoriPeriod("GB333333", Some("2001-01-20T00:00:00Z"), Some("1999-01-20T00:00:00Z"))
      )

      val updatedTraderData = traderData.copy(eoriHistory = historicEoris)

      val actualHeaderCarrier: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      when(mockHistoryService.getHistory(is(testEori))(actualHeaderCarrier.capture(), any())).thenReturn(Future.successful(historicEoris))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockEoriStore.findByEori(is(testEori)))
        .thenReturn(Future.successful(Some(traderData)) , Future.successful(Some(updatedTraderData)))

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory { eori }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"
      val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)

      val result = contentAsString(controller.handleRequest.apply(request))
      result mustBe s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"},{"eori":"GB222222"},{"eori":"GB333333"}]}}}"""

      verify(mockEoriStore).updateHistoricEoris(historicEoris)
      actualHeaderCarrier.getAllValues.asScala.map(_.requestId) mustBe List(Some(RequestId(queryRequestId)))
    }

    "return SERVICE_UNAVAILABLE when getSubscriberInformation throws ServiceUnavailableException" in new GraphQLScenario() {
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, None, None)), None)

      when(mockHistoryService.getHistory(is(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None))))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockEoriStore.findByEori(is(testEori)))
        .thenReturn(Future.successful(Some(traderData)))
      when(mockCustomerInfoService.getSubscriberInformation(is(testEori))(any()))
        .thenReturn(Future.failed(Upstream5xxResponse("ServiceUnavailable", 503, 503)))

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"
      val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)

      val result = controller.handleRequest()(request)
      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "retrieveAndStoreCustomerInformation" should {
      "propagate ServiceUnavailableException" in new GraphQLScenario() {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        when(mockCustomerInfoService.getSubscriberInformation(any())(any())).thenReturn(Future.failed(new ServiceUnavailableException("Boom")))
        assertThrows[ServiceUnavailableException](await(schema.retrieveAndStoreCustomerInformation(testEori)))
      }
    }
  }

  "GraphQL Mutations" should {
    "Insert by Eori" in new GraphQLScenario() {
      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      val query = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\" validFrom:\\"$testValidFrom\\" validUntil:\\"$testValidUntil\\"}, notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
      val request = authorizedRequest.withBody(Json.parse(query))
      val result = contentAsString(controller.handleRequest.apply(request))

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
      val result = contentAsString(controller.handleRequest.apply(request))

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

      val response = controller.handleRequest.apply(request)

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

}
