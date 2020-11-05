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
import org.mockito.Mockito.{never, times, verify, when}
import play.api.inject.bind
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, route, running, _}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.datastore.domain.onwire.MdgSub09DataModel
import uk.gov.hmrc.customs.datastore.domain.{EmailAddress, EoriPeriod, NotificationEmail, TraderData}
import uk.gov.hmrc.customs.datastore.services._
import uk.gov.hmrc.customs.datastore.utils.SpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.RequestId
import uk.gov.hmrc.mongo.MongoConnector

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.concurrent.Future

/**
  * These are integration tests from the GraphQL query down to Mongo db
  */
class GraphQLControllerIt extends SpecBase {

  trait Setup {

    val testEmail: EmailAddress = "bob@mail.com"
    val testEori = "GB111111"
    val testValidFrom = "20180101"
    val testValidUntil = "20200101"
    val testTimestamp = "timestamp"
    val historicEoris = Seq(
      EoriPeriod(testEori, Some("2010-01-20T00:00:00Z"), None),
      EoriPeriod("GB222222", Some("2002-01-20T00:00:00Z"), Some("2001-01-20T00:00:00Z")),
      EoriPeriod("GB333333", Some("2001-01-20T00:00:00Z"), Some("1999-01-20T00:00:00Z"))
    )
    val testSubscriberInfo = new MdgSub09DataModel(Some(testEmail), Some("2010-01-20T00:00:00Z"))

    val mongoComponent = new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }

    val authorizedRequest = FakeRequest(POST, routes.GraphQLController.handleRequest().url)
      .withHeaders(
        "Content-Type" -> "application/json",
        "Authorization" -> "Bearer secret-token"
      )
  }

  "GraphQL Queries" should {
    "not call the HistoricEoriService and the SubscriptionInfoService if we already have them cached" in new Setup {
      val mockHistoryService = mock[EoriHistoryService]
      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any())).thenReturn(Future.successful(historicEoris))

      val traderData = TraderData(
        Seq(EoriPeriod(testEori, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(Some(testEmail), None)))

      val mockCustomerInfoService = mock[SubscriptionInfoService]

      private val app = application
        .overrides(
          bind[ReactiveMongoComponent].toInstance(mongoComponent),
          bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
          bind[EoriHistoryService].toInstance(mockHistoryService)
        ).build()

      val eoriStore = app.injector.instanceOf[EoriStore]

      running(app) {
        await(for {
          _ <- eoriStore.removeAll()
          _ <- eoriStore.insert(traderData)
        } yield ())

        val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""
        val result = route(app, authorizedRequest.withBody(Json.parse(select))).value

        contentAsJson(result) mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"}],"notificationEmail":{"address":"$testEmail"}}}}""")

        verify(mockHistoryService, never()).getHistory(any())(any(), any())
        verify(mockCustomerInfoService, never()).getSubscriberInformation(any())(any())
      }
    }

    "call the HistoricEoriService but not the SubscriptionInfoService if we already have emails but not historic eoris" in new Setup {
      val mockHistoryService = mock[EoriHistoryService]
      val mockCustomerInfoService = mock[SubscriptionInfoService]

      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any())).thenReturn(Future.successful(historicEoris))

      val traderData = TraderData(
        Seq(EoriPeriod(testEori, None, None)),
        Some(NotificationEmail(Some(testEmail), None)))

      private val app = application
        .overrides(
          bind[ReactiveMongoComponent].toInstance(mongoComponent),
          bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
          bind[EoriHistoryService].toInstance(mockHistoryService)
        ).build()

      val eoriStore = app.injector.instanceOf[EoriStore]

      running(app) {
        await(for {
          _ <- eoriStore.removeAll()
          _ <- eoriStore.insert(traderData)
        } yield ())

        val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""
        val result = route(app, authorizedRequest.withBody(Json.parse(select))).value

        contentAsJson(result) mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"},{"eori":"GB222222"},{"eori":"GB333333"}],"notificationEmail":{"address":"$testEmail"}}}}""")
        verify(mockHistoryService, times(1)).getHistory(any())(any(), any())
        verify(mockCustomerInfoService, never()).getSubscriberInformation(any())(any())

      }
    }

    "call the SubscriptionInfoService but not the HistoricEoriService if we already have historic eoris but not emails" in new Setup {

      val mockHistoryService = mock[EoriHistoryService]
      val mockCustomerInfoService = mock[SubscriptionInfoService]

      when(mockCustomerInfoService.getSubscriberInformation(eqTo(testEori))(any())).thenReturn(Future.successful(Some(testSubscriberInfo)))

      val traderData = TraderData(
        Seq(EoriPeriod(testEori, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(None, None)))

      private val app = application
        .overrides(
          bind[ReactiveMongoComponent].toInstance(mongoComponent),
          bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
          bind[EoriHistoryService].toInstance(mockHistoryService)
        ).build()

      val eoriStore = app.injector.instanceOf[EoriStore]

      running(app) {
        await(for {
          _ <- eoriStore.removeAll()
          _ <- eoriStore.insert(traderData)
        } yield ())

        val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""
        val result = route(app, authorizedRequest.withBody(Json.parse(select))).value
        contentAsJson(result) mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"}],"notificationEmail":{"address":"$testEmail"}}}}""")
        verify(mockHistoryService, never()).getHistory(any())(any(), any())
        verify(mockCustomerInfoService, times(1)).getSubscriberInformation(any())(any())
      }
    }

    "call both the HistoricEoriService and the SubscriptionInfoService if we have no emails and no historic eoris" in new Setup {

      val mockHistoryService = mock[EoriHistoryService]
      val mockCustomerInfoService = mock[SubscriptionInfoService]

      when(mockHistoryService.getHistory(eqTo(testEori))(any(), any())).thenReturn(Future.successful(historicEoris))
      when(mockCustomerInfoService.getSubscriberInformation(eqTo(testEori))(any())).thenReturn(Future.successful(Some(testSubscriberInfo)))

      val traderData = TraderData(
        Seq(EoriPeriod(testEori, None, None)),
        Some(NotificationEmail(None, None)))

      private val app = application
        .overrides(
          bind[ReactiveMongoComponent].toInstance(mongoComponent),
          bind[SubscriptionInfoService].toInstance(mockCustomerInfoService),
          bind[EoriHistoryService].toInstance(mockHistoryService)
        ).build()

      val eoriStore = app.injector.instanceOf[EoriStore]

      running(app) {
        await(for {
          _ <- eoriStore.removeAll()
          _ <- eoriStore.insert(traderData)
        } yield ())

        val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""
        val result = route(app, authorizedRequest.withBody(Json.parse(select))).value
        contentAsJson(result) mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"},{"eori":"GB222222"},{"eori":"GB333333"}],"notificationEmail":{"address":"$testEmail"}}}}""")
        verify(mockHistoryService, times(1)).getHistory(any())(any(), any())
        verify(mockCustomerInfoService, times(1)).getSubscriberInformation(any())(any())

      }
    }
  }


  "Integration tests" should {
    "Add an email address to a trader's record, and then retrieve historic Eoris from the cache" in new Setup {
      val actualHeaderCarrier: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      val mockHistoryService = mock[EoriHistoryService]

      when(mockHistoryService.getHistory(eqTo(testEori))(actualHeaderCarrier.capture(), any()))
        .thenReturn(Future.successful(historicEoris))

      private val app = application
        .overrides(
          bind[ReactiveMongoComponent].toInstance(mongoComponent),
          bind[EoriHistoryService].toInstance(mockHistoryService)
        ).build()

      val mutation = s"""{"query" : "mutation {byEori(eoriHistory:{eori:\\"$testEori\\"}, notificationEmail: {address: \\"$testEmail\\", timestamp: \\"$testTimestamp\\"} )}" }"""
      val mutationRequestId = "mutate-this"
      val mutationRequest = authorizedRequest.withBody(Json.parse(mutation))
        .withHeaders("X-Request-ID" -> mutationRequestId)

      val query = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori validFrom validUntil},  notificationEmail { address, timestamp }  } }"}"""
      val queryRequest = authorizedRequest.withBody(Json.parse(query))
        .withHeaders("X-Request-ID" -> "can-i-haz-eori")

      running(app) {
        val testResult = await(route(app, mutationRequest).value)
        println(testResult)
        val result = route(app, queryRequest).value
        val json = contentAsJson(result)
        (json \\ "eori").map(a => a.as[JsString].value) mustBe List(testEori, "GB222222", "GB333333")
        (json \\ "address").head.as[JsString].value mustBe testEmail
        actualHeaderCarrier.getAllValues.asScala.map(_.requestId) mustBe List(Some(RequestId(mutationRequestId)))

      }
    }
  }
}

