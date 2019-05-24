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
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.customs.datastore.domain.SubscriptionEmail
import uk.gov.hmrc.customs.datastore.util.ReactiveMongoComponent

import scala.concurrent.ExecutionContext.Implicits.global

class EmailStoreSpec extends WordSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits with BeforeAndAfterEach {

  override def beforeEach: Unit = {
    emailStore.drop
  }

  val reactiveMongo: ReactiveMongoComponent = new ReactiveMongoComponent {
    override def getDb(dbName: String): () => DefaultDB = mongo
  }

  val emailStore = new EmailStore(reactiveMongo)

  val eori = "EORI00000001"
  val emailAddress = "test@test.com"

  val eori1 = "EORI100000001"
  val emailAddress1 = "test1@test.com"

  val eori2 = "EORI20000001"
  val emailAddress2 = "test2@test.com"

  "emailStore" should {
    "store and retrieve email address for given eori number" in {
      val result = for {
        _ <- emailStore.save(eori, emailAddress)
        email <- emailStore.retrieve(eori)
      } yield email

      await(result) mustBe Some(SubscriptionEmail(eori, emailAddress))
    }

    "retrieve all email addresses with associated eori number" in {
      val result = for {
        _ <- emailStore.save(eori1, emailAddress1)
        _ <- emailStore.save(eori2, emailAddress2)
        email <- emailStore.retrieveAll()
      } yield email

      await(result) mustBe List(SubscriptionEmail(eori1, emailAddress1), SubscriptionEmail(eori2, emailAddress2))
    }
  }
}
