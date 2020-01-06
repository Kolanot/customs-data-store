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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => is}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers.POST
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.onwire.MdgSub09DataModel
import uk.gov.hmrc.customs.datastore.domain.{EmailAddress, EoriPeriod, NotificationEmail, TraderData}
import uk.gov.hmrc.customs.datastore.graphql.{GraphQL, TraderDataSchema}
import uk.gov.hmrc.customs.datastore.services._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.RequestId
import uk.gov.hmrc.mongo.MongoConnector

import scala.collection.JavaConverters._
import scala.concurrent.Future

/**
  * These are integration tests from the GraphQL query down to Mongo db
  */
class GraphQLControllerIt extends WordSpec with MongoSpecSupport with MockitoSugar with MustMatchers with FutureAwaits with DefaultAwaitTimeout {

  val endPoint = "/graphql"
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
  val testSubscriberInfo = new MdgSub09DataModel(Some(testEmail),Some("2010-01-20T00:00:00Z"))

  class GraphQLScenario() {

    val as = ActorSystem("EoriStoreAs")
    val materializer = ActorMaterializer()(as)

    val mockHistoryService = mock[EoriHistoryService]
    val mockCustomerInfoService = mock[SubscriptionInfoService]
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    val appConfig = new AppConfig(configuration, env)
    val authConnector = new ServerTokenAuthorization(appConfig)

    val eoriStore = new EoriStore(new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }, appConfig)
    FeatureSwitch.DataStore.enable()

    val schema = new TraderDataSchema(eoriStore, mockHistoryService, mockCustomerInfoService)
    val graphQL = new GraphQL(schema)
    val controller = new GraphQLController(authConnector, graphQL)
    val authorizedRequest = FakeRequest(POST, "/graphql").withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer secret-token")

  }


  "GraphQL Queries" should {
    "not call the HistoricEoriService and the SubscriptionInfoService if we already have them cached" in new GraphQLScenario() {
      when(mockHistoryService.getHistory(is(testEori))(any(), any())).thenReturn(Future.successful(historicEoris))
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(Some(testEmail), None)))
      await(for {
        _ <- eoriStore.removeAll()
        _ <- eoriStore.insert(traderData)
      } yield())

      val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""

      val result = await(for {
        queryResult <-  controller.handleQuery.apply(authorizedRequest.withBody(Json.parse(select)))
        byteString <- queryResult.body.consumeData(materializer)
        json <- Future.successful(Json.parse(byteString.utf8String))
      } yield json)

      result mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"}],"notificationEmail":{"address":"$testEmail"}}}}""")
      verify(mockHistoryService,never()).getHistory(any())(any(), any())
      verify(mockCustomerInfoService,never()).getSubscriberInformation(any())(any())
    }

    "call the HistoricEoriService but not the SubscriptionInfoService if we already have emails but not historic eoris" in new GraphQLScenario() {
      when(mockHistoryService.getHistory(is(testEori))(any(), any())).thenReturn(Future.successful(historicEoris))
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, None, None)),
        Some(NotificationEmail(Some(testEmail), None)))
      await(for {
        _ <- eoriStore.removeAll()
        _ <- eoriStore.insert(traderData)
      } yield())

      val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""

      val result = await(for {
        queryResult <-  controller.handleQuery.apply(authorizedRequest.withBody(Json.parse(select)))
        byteString <- queryResult.body.consumeData(materializer)
        json <- Future.successful(Json.parse(byteString.utf8String))
      } yield json)

      result mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"},{"eori":"GB222222"},{"eori":"GB333333"}],"notificationEmail":{"address":"$testEmail"}}}}""")
      verify(mockHistoryService,times(1)).getHistory(any())(any(), any())
      verify(mockCustomerInfoService,never()).getSubscriberInformation(any())(any())
    }

    "call the SubscriptionInfoService but not the HistoricEoriService if we already have historic eoris but not emails" in new GraphQLScenario() {
      when(mockCustomerInfoService.getSubscriberInformation(is(testEori))(any())).thenReturn(Future.successful(Some(testSubscriberInfo)))
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, Some("2001-01-20T00:00:00Z"), None)),
        Some(NotificationEmail(None, None)))
      await(for {
        _ <- eoriStore.removeAll()
        _ <- eoriStore.insert(traderData)
      } yield())

      val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""

      val result = await(for {
        queryResult <-  controller.handleQuery.apply(authorizedRequest.withBody(Json.parse(select)))
        byteString <- queryResult.body.consumeData(materializer)
        json <- Future.successful(Json.parse(byteString.utf8String))
      } yield json)

      result mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"}],"notificationEmail":{"address":"$testEmail"}}}}""")
      verify(mockHistoryService,never()).getHistory(any())(any(), any())
      verify(mockCustomerInfoService,times(1)).getSubscriberInformation(any())(any())
    }

    "call both the HistoricEoriService and the SubscriptionInfoService if we have no emails and no historic eoris" in new GraphQLScenario() {
      when(mockHistoryService.getHistory(is(testEori))(any(), any())).thenReturn(Future.successful(historicEoris))
      when(mockCustomerInfoService.getSubscriberInformation(is(testEori))(any())).thenReturn(Future.successful(Some(testSubscriberInfo)))
      val traderData = TraderData(
        Seq(EoriPeriod(testEori, None, None)),
        Some(NotificationEmail(None, None)))
      await(for {
        _ <- eoriStore.removeAll()
        _ <- eoriStore.insert(traderData)
      } yield())

      val select = s"""{ "query": "query { byEori( eori: \\"$testEori\\") { eoriHistory {eori} notificationEmail { address }  } }"}"""

      val result = await(for {
        queryResult <-  controller.handleQuery.apply(authorizedRequest.withBody(Json.parse(select)))
        byteString <- queryResult.body.consumeData(materializer)
        json <- Future.successful(Json.parse(byteString.utf8String))
      } yield json)

      result mustBe Json.parse(s"""{"data":{"byEori":{"eoriHistory":[{"eori":"$testEori"},{"eori":"GB222222"},{"eori":"GB333333"}],"notificationEmail":{"address":"$testEmail"}}}}""")
      verify(mockHistoryService,times(1)).getHistory(any())(any(), any())
      verify(mockCustomerInfoService,times(1)).getSubscriberInformation(any())(any())
    }

    "pass the incoming header carrier along " in {
      pending   //TODO Add a testcase here
    }

  }


  "Integration tests" should {
    "Add an email address to a trader's record, and then retrieve historic Eoris from the cache" in new GraphQLScenario{
      val actualHeaderCarrier: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      when(mockHistoryService.getHistory(is(testEori))(actualHeaderCarrier.capture(), any()))
        .thenReturn(Future.successful(historicEoris))

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

      (result \\ "eori").map(a => a.as[JsString].value) mustBe List(testEori, "GB222222" ,"GB333333")
      (result \\ "address").head.as[JsString].value mustBe testEmail
      actualHeaderCarrier.getAllValues.asScala.map(_.requestId) mustBe List(Some(RequestId(mutationRequestId)))
    }
  }
}

