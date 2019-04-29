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

import org.scalatest.{FlatSpec, MustMatchers}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.customs.datastore.domain.{EORIHistory, EoriHistoryResponse}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global

class EoriStoreSpec extends FlatSpec with MustMatchers with MongoSpecSupport with DefaultAwaitTimeout with FutureAwaits {

  val reactiveMongo = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  val cache = new EoriStore(reactiveMongo)
  val eori1 = "EORI12345678"

  it must "put a single EORI into the database" in {
    val newEori = EORIHistory(eori1, Some("1985-03-20T19:30:51Z"),None)
    val result = for {
      _ <- cache.eoriAdd(newEori)
      storedEori <- cache.eoriGet(eori1)
    } yield storedEori

    await(result) mustBe Some(EoriHistoryResponse(Seq(newEori)))
  }

}
