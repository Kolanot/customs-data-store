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
import uk.gov.hmrc.customs.datastore.graphql.{EoriPeriodInput, InputEmail}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class EoriStore @Inject()(mongoComponent: ReactiveMongoComponent)
  extends {
    val FieldEoriHistory = "eoriHistory"
    val FieldEori = "eori"
    val EoriSearchKey = s"$FieldEoriHistory.$FieldEori"
    val FieldEoriValidFrom = "validFrom"
    val FieldEoriValidUntil = "validUntil"
    val FieldEmails = "notificationEmail"
    val FieldEmailAddress = "address"
    val FieldTimestamp = "timestamp"
    val EmailAddressSearchKey = s"$FieldEmails.$FieldEmailAddress"
    val EmailTimestampSearchKey = s"$FieldEmails.$FieldTimestamp"

  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true)
  )

  def saveEoris(eoriHistory: Seq[EoriPeriod]): Future[Any] = {
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> Json.obj("$in" -> eoriHistory.map(_.eori))),
      update = Json.obj("$set" -> Json.obj(FieldEoriHistory -> Json.toJson(eoriHistory))),
      upsert = true
    )
  }

  def findByEori(eori: String): Future[Option[TraderData]] = {
    find(EoriSearchKey -> eori).map(_.headOption)
  }

  def upsertByEori(eoriPeriod: EoriPeriodInput, email: Option[InputEmail]): Future[Boolean] = {
    val updateEmailAddress = email.flatMap(_.address).map(address => (EmailAddressSearchKey -> toJsFieldJsValueWrapper(address)))
    val updateEmailTimestamp = email.flatMap(_.timestamp).map(timestamp => (EmailTimestampSearchKey -> toJsFieldJsValueWrapper(timestamp)))
    val updateEmailFields = Seq(updateEmailAddress, updateEmailTimestamp).flatten

    val updateEoriValidFrom = eoriPeriod.validFrom.map(vFrom => (FieldEoriValidFrom -> toJsFieldJsValueWrapper(vFrom)))
    val updateEoriValidUntil = eoriPeriod.validUntil.map(vUntil => (FieldEoriValidUntil -> toJsFieldJsValueWrapper(vUntil)))
    val updateEori = Option(FieldEori -> toJsFieldJsValueWrapper(eoriPeriod.eori))
    val updateHistoricEoriFields = Seq(updateEoriValidFrom, updateEoriValidUntil, updateEori).flatten
    val historicEoriInnerJson = Json.obj(updateHistoricEoriFields: _*)
    val eoriHistoryJson = FieldEoriHistory -> toJsFieldJsValueWrapper(Json.arr(toJsFieldJsValueWrapper(historicEoriInnerJson)))

    val updateFields = Json.obj(eoriHistoryJson +: updateEmailFields :_*)
    val result = findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriod.eori),
      update = Json.obj("$set" -> toJsFieldJsValueWrapper(updateFields)),
      upsert = true
    )
    result.map(_.lastError.flatMap(_.err).isEmpty)
  }

}

