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

import javax.inject._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.customs.datastore.domain.TraderData._
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class EoriStore @Inject()(mongoComponent: ReactiveMongoComponent)
  extends {
    val FieldEori = classOf[EoriPeriod].getDeclaredFields.apply(0).getName
    val FieldEoriHistory = classOf[TraderData].getDeclaredFields.apply(1).getName
    val FieldEmails = classOf[TraderData].getDeclaredFields.apply(2).getName
    val FieldEmailAddress = classOf[Email].getDeclaredFields.apply(0).getName
    val EoriSearchKey = s"$FieldEoriHistory.$FieldEori"
    val EmailSearchKey = s"$FieldEmails.$FieldEmailAddress"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {
  println("FieldEmails: " + FieldEmails)
  println("EmailSearchKeyL " + EmailSearchKey)

  override def indexes: Seq[Index] = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true),
    Index(Seq(EmailSearchKey -> IndexType.Ascending), name = Some(FieldEmails + FieldEmailAddress + "Index"), unique = true, sparse = true))

  def saveEoris(eoriHistory: Seq[EoriPeriod]): Future[Any] = {
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> Json.obj("$in" -> eoriHistory.map(_.eori))),
      update = Json.obj("$set" -> Json.obj(FieldEoriHistory -> Json.toJson(eoriHistory))),
      upsert = true
    )
  }

  def getTraderData(eori: String): Future[Option[TraderData]] = {
    find(EoriSearchKey -> eori).map(_.headOption)
  }

  def getEmail(eori: Eori): Future[Option[Email]] = {
    getTraderData(eori).map(traderData => traderData.flatMap(_.notificationEmail))
  }

  def saveEmail(eori: Eori, email: Email): Future[Any] = {
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eori),
      update = Json.obj(
        "$setOnInsert" -> Json.obj(FieldEoriHistory -> Json.arr(Json.obj(FieldEori -> eori))),
        "$set" -> Json.obj(FieldEmails -> email)),
      upsert = true
    )
  }

  def rosmInsert(credId: CredentialId, eori: Eori, email: String, isValidated: Boolean): Future[Boolean] = {
    //TODO: When someone registered for a new Eori, they will call this endpoint to save the data
    Future.successful(true)
  }

}