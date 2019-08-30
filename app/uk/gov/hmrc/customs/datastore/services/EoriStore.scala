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
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.json.Json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.customs.datastore.domain.TraderData._
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{EoriPeriodInput, InputEmail}
import uk.gov.hmrc.customs.datastore.util.TTLIndexing
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class EoriStore @Inject()(mongoComponent: ReactiveMongoComponent)
  extends {
    val TheTraderData = "traderData"
    val FieldEoriHistory = "eoriHistory"
    val FieldEori = "eori"
    val EoriSearchKey = s"$TheTraderData.$FieldEoriHistory.$FieldEori"
    val FieldEoriValidFrom = "validFrom"
    val FieldEoriValidUntil = "validUntil"
    val FieldEmails = "notificationEmail"
    val FieldEmailAddress = "address"
    val FieldTimestamp = "timestamp"
    val EmailAddressSearchKey = s"$TheTraderData.$FieldEmails.$FieldEmailAddress"
    val EmailTimestampSearchKey = s"$TheTraderData.$FieldEmails.$FieldTimestamp"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) with TTLIndexing[TraderData, BSONObjectID] {

  override val expireAfterSeconds: Long = 10

  override def additionalIndexes = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(TheTraderData + FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true)
  )

  def findByEori(eori: String): Future[Option[TraderData]] = {
    import reactivemongo.play.json.BSONDocumentWrites
    val selector = BSONDocument(EoriSearchKey -> eori)
    collection.find(selector).one[JsObject].map(_.map { obj => (obj \ TheTraderData).get.as[TraderData] })
  }

  def getLastUpdated() = "lastUpdated" -> toJsFieldJsValueWrapper(DateTime.now(DateTimeZone.UTC))(ReactiveMongoFormats.dateTimeWrite)

  //TODO Error if you had email addresses this will overwrite it (remove it)
  def saveEoris(eoriHistories: Seq[EoriPeriod]): Future[Any] = {
    val eoriHistoryJson = Json.obj(FieldEoriHistory -> toJsFieldJsValueWrapper(eoriHistories))
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> Json.obj("$in" -> eoriHistories.map(_.eori))),
      update = Json.obj("$set" -> Json.obj(getLastUpdated, TheTraderData -> eoriHistoryJson)),
      upsert = true
    )
  }

  //TODO Error if you have historic eoris this will overwrite them with the eori that was passed in
  def upsertByEori(eoriPeriod: EoriPeriodInput, email: Option[InputEmail]): Future[Boolean] = {
    val updateEmailAddress = email.flatMap(_.address).map(address => (EmailAddressSearchKey -> toJsFieldJsValueWrapper(address)))
    val updateEmailTimestamp = email.flatMap(_.timestamp).map(timestamp => (EmailTimestampSearchKey -> toJsFieldJsValueWrapper(timestamp)))
    val updateEmailFields = Seq(updateEmailAddress, updateEmailTimestamp).flatten

    val updateEoriValidFrom = eoriPeriod.validFrom.map(vFrom => (FieldEoriValidFrom -> toJsFieldJsValueWrapper(vFrom)))
    val updateEoriValidUntil = eoriPeriod.validUntil.map(vUntil => (FieldEoriValidUntil -> toJsFieldJsValueWrapper(vUntil)))
    val updateEori = Option(FieldEori -> toJsFieldJsValueWrapper(eoriPeriod.eori))
    val updateHistoricEoriFields = Seq(updateEoriValidFrom, updateEoriValidUntil, updateEori).flatten
    val historicEoriInnerJson = Json.obj(updateHistoricEoriFields: _*)
    val eoriHistoryJson = s"$TheTraderData.$FieldEoriHistory" -> toJsFieldJsValueWrapper(Json.arr(toJsFieldJsValueWrapper(historicEoriInnerJson)))

    val updateFields = Json.obj(getLastUpdated +: eoriHistoryJson +: updateEmailFields: _*)
    val result = findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriod.eori),
      update = Json.obj("$set" -> toJsFieldJsValueWrapper(updateFields)),
      upsert = true
    )
    result.map(_.lastError.flatMap(_.err).isEmpty)
  }


  //TODO remove this when the above two methods are fixed
  def temporaryInsert(traderData: TraderData): Future[Boolean] = {
    val allData = Json.obj(getLastUpdated, TheTraderData -> toJsFieldJsValueWrapper(traderData))
    val result = findAndUpdate(
      query = Json.obj(EoriSearchKey -> traderData.eoriHistory.head.eori),
      update = Json.obj("$set" -> allData),
      upsert = true
    )
    result.map(_.lastError.flatMap(_.err).isEmpty)
  }

}

