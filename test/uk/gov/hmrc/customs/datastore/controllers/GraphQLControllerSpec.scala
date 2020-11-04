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
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.{never, verify, when}
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, _}
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.TraderDataSchema
import uk.gov.hmrc.customs.datastore.services._
import uk.gov.hmrc.customs.datastore.utils.SpecBase
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.RequestId

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.Future


class GraphQLControllerSpec extends SpecBase {

  trait Setup {

    val endPoint = "/graphql"
    val testEmail: EmailAddress = "bob@mail.com"
    val testEori = "GB111111"
    val testValidFrom = "20180101"
    val testValidUntil = "20200101"
    val testTimestamp = "timestamp"

    val mockMetricsReporterService = mock[MetricsReporterService]

    val authorizedRequest = FakeRequest(POST, routes.GraphQLController.handleRequest().url)
      .withHeaders(
        "Content-Type" -> "application/json",
        "Authorization" -> "Bearer secret-token"
      )

  }

  "GraphQL Queries" should {
    "return unauthorised exception when auth token is not present" in new Setup {

      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]
      val mockCustomerInfoService: SubscriptionInfoService = mock[SubscriptionInfoService]

      val eoriNumber: Eori = "GB12345678"
      val query = s"""{ "query": "query { byEori( eori: \\"$eoriNumber\\") { notificationEmail { address }  } }"}"""

      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("1987.03.20"), None))))

      private val app = application.overrides(
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {

        val request = FakeRequest(POST, routes.GraphQLController.handleRequest().url)
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(Json.parse(query))

        val result = route(app, request).value

        status(result) mustBe UNAUTHORIZED
      }
    }

    "not call the HistoricEoriService if we already have them cached" in new Setup {
      val mockEoriStore: EoriStore = mock[EoriStore]

      val traderData = TraderData(
        Seq(EoriPeriod(testEori, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(Some(testEmail), None)))

      when(mockEoriStore.findByEori(eqTo(testEori))).thenReturn(Future.successful(Some(traderData)))
      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query))
        val result = route(app, request).value
        contentAsString(result) mustBe s"""{"data":{"byEori":{"notificationEmail":{"address":"$testEmail"}}}}"""
      }
    }

    "be able to request emails" in new Setup() {

      val verifiedEmail = NotificationEmail(Some(testEmail), Some(testTimestamp))
      val traderData = TraderData(Seq(EoriPeriod(testEori, None, None)), Some(verifiedEmail))
      val mockEoriStore: EoriStore = mock[EoriStore]
      val query = s"""{ "query": "query { getEmail( eori: \\"$testEori\\") { address } }"}"""

      when(mockEoriStore.findByEori(eqTo(testEori))).thenReturn(Future.successful(Some(traderData)))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> "requestId")
        val result = route(app, request).value
        contentAsString(result) must include(verifiedEmail.address.get)
      }
    }

    "call the HistoricEoriService if we don't have them cached" in new Setup {
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
      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory { eori }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"

      when(mockHistoryService.getHistory(eqTo(testEori))(actualHeaderCarrier.capture(), any())).thenReturn(Future.successful(historicEoris))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockEoriStore.findByEori(eqTo(testEori)))
        .thenReturn(Future.successful(Some(traderData)), Future.successful(Some(updatedTraderData)))


      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)
        val result = route(app, request).value
        contentAsString(result) mustBe s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"},{"eori":"GB222222"},{"eori":"GB333333"}]}}}"""
        verify(mockEoriStore).updateHistoricEoris(historicEoris)
        actualHeaderCarrier.getAllValues.asScala.map(_.requestId) mustBe List(Some(RequestId(queryRequestId)))
      }
    }

    "return 503 when getSubscriberInformation throws Upstream5xxResponse" in new Setup {
      val traderData = TraderData(Seq(EoriPeriod(testEori, None, None)), None)
      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"

      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]
      val mockCustomerInfoService = mock[SubscriptionInfoService]

      when(mockEoriStore.findByEori(eqTo(testEori)))
        .thenReturn(Future.successful(Some(traderData)))
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None))))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockCustomerInfoService.getSubscriberInformation(eqTo(testEori))(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("ServiceUnavailable", 503, 503)))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)
        val result = route(app, request).value
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }

    "return 503 when getSubscriberInformation throws GatewayTimeoutException" in new Setup {
      val traderData = TraderData(Seq(EoriPeriod(testEori, None, None)), None)
      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"

      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]
      val mockCustomerInfoService = mock[SubscriptionInfoService]

      when(mockEoriStore.findByEori(eqTo(testEori)))
        .thenReturn(Future.successful(Some(traderData)))
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None))))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockCustomerInfoService.getSubscriberInformation(eqTo(testEori))(any()))
        .thenReturn(Future.failed(new GatewayTimeoutException("nope")))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)
        val result = route(app, request).value
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }

    "return 503 when getHistory throws Upstream5xxResponse" in new Setup {
      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"

      val traderData = TraderData(Seq(EoriPeriod(testEori, None, None)), None)

      when(mockEoriStore.findByEori(eqTo(testEori)))
        .thenReturn(Future.successful(Some(traderData)))
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.failed(Upstream5xxResponse("ServiceUnavailable", 503, 503)))


      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)
        val result = route(app, request).value
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }

    "return 503 when getHistory throws GatewayTimeoutException" in new Setup {
      val traderData = TraderData(Seq(EoriPeriod(testEori, None, None)), None)

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { notificationEmail { address }  } }"}"""
      val queryRequestId = "can-i-haz-eori-history"

      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

      when(mockEoriStore.findByEori(eqTo(testEori)))
        .thenReturn(Future.successful(Some(traderData)))
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.failed(new GatewayTimeoutException("nope")))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val request = authorizedRequest.withBody(Json.parse(query)).withHeaders("X-Request-ID" -> queryRequestId)
        val result = route(app, request).value
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }

    "retrieveAndStoreCustomerInformation" should {
      "propagate ServiceUnavailableException" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val mockCustomerInfoService = mock[SubscriptionInfoService]
        val mockEoriStore: EoriStore = mock[EoriStore]
        val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

        val schema = new TraderDataSchema(mockEoriStore, mockHistoryService, mockCustomerInfoService)

        when(mockCustomerInfoService.getSubscriberInformation(any())(any())).thenReturn(Future.failed(new ServiceUnavailableException("Boom")))
        assertThrows[ServiceUnavailableException](await(schema.retrieveAndStoreCustomerInformation(testEori)))
      }
    }
  }

  "GraphQL Mutations" should {
    "Insert by Eori" in new Setup {
      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))

      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("1987.03.20"), None))))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val query = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\" validFrom:\\"$testValidFrom\\" validUntil:\\"$testValidUntil\\"}, notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
        val request = authorizedRequest.withBody(Json.parse(query))


        val result = route(app, request).value
        contentAsString(result) must include("data")
        contentAsString(result) mustNot include("errors")

        val eoriPeriod = EoriPeriod(testEori, Some(testValidFrom), Some(testValidUntil))
        val email = NotificationEmail(Some(testEmail), Some(testTimestamp))
        verify(mockEoriStore).upsertByEori(eoriPeriod, Some(email))
      }
    }

    "Insert an EORI with no email address" in new Setup {
      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("1987.03.20"), None))))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()


      running(app) {
        val query = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\" validFrom:\\"$testValidFrom\\" validUntil:\\"$testValidUntil\\"} )}" }"""
        val request = authorizedRequest.withBody(Json.parse(query))
        val result = route(app, request).value
        contentAsString(result) must include("data")
        contentAsString(result) mustNot include("errors")

        val eoriPeriod = EoriPeriod(testEori, Some(testValidFrom), Some(testValidUntil))
        verify(mockEoriStore).upsertByEori(eoriPeriod, None)
      }
    }

    "Return an error response when queried without an EORI" in new Setup {

      val mockEoriStore: EoriStore = mock[EoriStore]
      val mockHistoryService: EoriHistoryService = mock[EoriHistoryService]

      when(mockEoriStore.upsertByEori(any(), any())).thenReturn(Future.successful(true))
      when(mockEoriStore.updateHistoricEoris(any())).thenReturn(Future.successful(true))
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any()))
        .thenReturn(Future.successful(Seq(EoriPeriod(testEori, Some("1987.03.20"), None))))

      private val app = application.overrides(
        bind[EoriStore].toInstance(mockEoriStore),
        bind[EoriHistoryService].toInstance(mockHistoryService),
        bind[MetricsReporterService].toInstance(mockMetricsReporterService)
      ).build()

      running(app) {
        val query = s"""{"query" : "mutation {byEori(notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
        val request = authorizedRequest.withBody(Json.parse(query))
        val result = route(app, request).value
        contentAsString(result) must include("data")
        contentAsString(result) must include("errors")
        contentAsString(result) must include("not provided")

        verify(mockHistoryService, never()).getHistory(any())(any(), any())
        verify(mockEoriStore, never()).upsertByEori(any(), any())
        verify(mockEoriStore, never()).updateHistoricEoris(any())
      }
    }
  }
}
