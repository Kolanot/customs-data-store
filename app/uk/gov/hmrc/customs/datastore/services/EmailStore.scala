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

import javax.inject.Inject
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.customs.datastore.domain.{EmailAddress, Eori, SubscriptionEmail}
import uk.gov.hmrc.customs.datastore.util.ReactiveMongoComponent
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

class EmailStore @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[SubscriptionEmail, BSONObjectID](
    collectionName = "emailStore",
    mongo = mongoComponent.getDb("manage-subscription"),
    domainFormat = SubscriptionEmail.jsonFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {

  //Added this to replicate implementation in customs-manage-subscription
  def save(eori: Eori, email: EmailAddress)(implicit ec: ExecutionContext): Future[Any] = {
    findAndUpdate(
      query = Json.obj("_id" -> eori),
      update = Json.obj("value" -> email),
      upsert = true
    )
  }

  def retrieve(eori: Eori)(implicit ec: ExecutionContext): Future[Option[SubscriptionEmail]] = {
    find("_id" -> eori).map(_.headOption)
  }

  def retrieveAll()(implicit ec: ExecutionContext): Future[List[SubscriptionEmail]] = {
    findAll()
  }
}
