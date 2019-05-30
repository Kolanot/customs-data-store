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

package uk.gov.hmrc.customs.datastore.services

import org.scalatest.{BeforeAndAfterEach, MustMatchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.customs.datastore._
import uk.gov.hmrc.customs.datastore.domain.{NotificationEmail, SubscriptionEmail}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionEmailServiceSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    await(emailStore.removeAll())
    await(eoriStore.removeAll())
  }

  val emailMongo: util.ReactiveMongoComponent = new util.ReactiveMongoComponent {
    override def getDb(dbName: String): () => DefaultDB = MongoConnector("mongodb://127.0.0.1:27017/test-customs-manage-subscription").db
  }

  val eoriMongo: ReactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  val emailStore = new EmailStore(emailMongo)
  val eoriStore = new EoriStore(eoriMongo)
  val subscriptionEmailService = new SubscriptionEmailService(eoriStore, emailStore)

  "SubscriptionEmailService" should {

    "not fail when no email addresses retrieved from manage subscription" in {
      val result = for {
        _ <- subscriptionEmailService.run()
        emails <- eoriStore.getEmail("1")
      } yield emails

      await(result) mustBe None
    }

    "not remove email addresses while retrieving from manage subscription" in {
      val result = for {
        _ <- emailStore.save("1", "test1@test.com")
        _ <- subscriptionEmailService.run()
        emails <- emailStore.retrieveAll()
      } yield emails

      await(result) mustBe List(SubscriptionEmail("1", "test1@test.com"))
    }

    "retrieve email addresses and store in the data store" in {
      await(for {
        _ <- emailStore.save("1", "test1@test.com")
        _ <- subscriptionEmailService.run()
        _ <- emailStore.save("2", "test2@test.com")
        _ <- emailStore.save("2", "test3@test.com")
        _ <- emailStore.save("1", "test11@test.com")
        _ <- subscriptionEmailService.run()
        email1 <- eoriStore.getEmail("1")
        _ <- Future.successful(email1 mustBe Some(NotificationEmail("test11@test.com",false)))
        email2 <- eoriStore.getEmail("2")
        _ <- Future.successful(email2 mustBe Some(NotificationEmail("test3@test.com", false)))
      } yield ())
    }

    "retrieve email address and overwrite the data in the data store" in {
      val result = for {
        _ <- emailStore.save("1", "test1@test.com")
        _ <- eoriStore.saveEmail("1", NotificationEmail("test1@test.com", true))
        _ <- subscriptionEmailService.run()
        email <- eoriStore.getEmail("1")
      } yield email

      await(result) mustBe Some(NotificationEmail("test1@test.com", false))
    }
  }
}