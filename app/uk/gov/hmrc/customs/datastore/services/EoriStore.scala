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
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.domain.TraderData._
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.graphql.{EoriPeriodInput, InputEmail}
import uk.gov.hmrc.customs.datastore.util.TTLIndexing
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Schema {
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

@Singleton
class EoriStore @Inject()(mongoComponent: ReactiveMongoComponent, appConfig: AppConfig)
  extends ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) with TTLIndexing[TraderData, BSONObjectID] {

  import Schema._

  override val expireAfterSeconds: Long = appConfig.dbTimeToLive

  override def indexes = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true)
  )

  def findByEori(eori: Eori): Future[Option[TraderData]] = {
    val selector = BSONDocument(EoriSearchKey -> eori)
    find(EoriSearchKey -> eori).map(_.headOption)
  }

  def updateHistoricEoris(eoriHistories: Seq[EoriPeriod]): Future[Any] = {
    val eoriHistoryJson = FieldEoriHistory -> toJsFieldJsValueWrapper(eoriHistories)
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> Json.obj("$in" -> eoriHistories.map(_.eori))),
      update = Json.obj("$set" -> Json.obj(getLastUpdated(), eoriHistoryJson)),
      upsert = true
    )
  }

  def upsertByEori(eoriPeriodInput: EoriPeriodInput, email: Option[InputEmail]): Future[Boolean] = {
    findByEori(eoriPeriodInput.eori).flatMap {
      case Some(traderData) => updateTraderData(eoriPeriodInput, email)
      case None => insertTraderData(eoriPeriodInput, email)
    }.map(_.lastError.flatMap(_.err).isEmpty)
  }

  private def getLastUpdated() = FieldLastUpdated -> toJsFieldJsValueWrapper(DateTime.now(DateTimeZone.UTC))(ReactiveMongoFormats.dateTimeWrite)

  private def emailChangeSet(email: Option[InputEmail]) = {
    Seq(
      (EmailAddressSearchKey -> email.flatMap(_.address)),
      (EmailTimestampSearchKey -> email.flatMap(_.timestamp))
    ).collect { case (field, Some(value)) => (field -> toJsFieldJsValueWrapper(value)) }
  }

  private def eoriHistoryChangeSet(eoriPeriodInput: EoriPeriodInput) = {
    Seq(
      FieldEori -> Some(eoriPeriodInput.eori),
      FieldEoriValidFrom -> eoriPeriodInput.validFrom,
      FieldEoriValidUntil -> eoriPeriodInput.validUntil
    ).collect { case (field, Some(value)) => (field -> toJsFieldJsValueWrapper(value))}
  }

  private def updateEoriHistory(eoriPeriodInput: EoriPeriodInput) = {
    val allEoriUpdates = eoriHistoryChangeSet(eoriPeriodInput).map(eu => (FieldEoriHistory + ".$[x]." + eu._1 -> eu._2))
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriodInput.eori),
      update = Json.obj("$set" -> Json.obj(allEoriUpdates: _*)),
      upsert = true,
      arrayFilters = Seq(Json.obj(s"x.$FieldEori" -> eoriPeriodInput.eori))
    )
  }

  private def updateEmailAndLastUpdated(eoriPeriodInput: EoriPeriodInput, email: Option[InputEmail]) = {
    val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(getLastUpdated +: emailChangeSet(email): _*)))
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriodInput.eori),
      update = Json.obj(updateSet),
      upsert = true
    )
  }

  private def insertTraderData(eoriPeriodInput: EoriPeriodInput, email: Option[InputEmail]) = {
    val eoriUpdates = FieldEoriHistory -> toJsFieldJsValueWrapper(Json.arr(Json.obj(eoriHistoryChangeSet(eoriPeriodInput): _*)))
    val changeSet = Json.obj(getLastUpdated +: eoriUpdates +: emailChangeSet(email): _*)
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriodInput.eori),
      update = Json.obj("$set" -> changeSet),
      upsert = true
    )
  }

  private def updateTraderData(eoriPeriodInput: EoriPeriodInput, email: Option[InputEmail]) = {
    updateEoriHistory(eoriPeriodInput).flatMap { otherQuery =>
      updateEmailAndLastUpdated(eoriPeriodInput, email)
    }
  }

}
