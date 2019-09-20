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
    val eoriHistoryChangeSet = FieldEoriHistory -> toJsFieldJsValueWrapper(eoriHistories)
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> Json.obj("$in" -> eoriHistories.map(_.eori))),
      update = Json.obj("$set" -> Json.obj(lastUpdatedChangeSet(), eoriHistoryChangeSet)),
      upsert = true
    )
  }

  def upsertByEori(eoriPeriod: EoriPeriod, email: Option[NotificationEmail]): Future[Boolean] = {
    findByEori(eoriPeriod.eori).flatMap {
      case Some(traderData) => updateTraderData(eoriPeriod, email)
      case None => insertTraderData(eoriPeriod, email)
    }.map(_.lastError.flatMap(_.err).isEmpty)
  }

  private def lastUpdatedChangeSet() = FieldLastUpdated -> toJsFieldJsValueWrapper(DateTime.now(DateTimeZone.UTC))(ReactiveMongoFormats.dateTimeWrite)

  private def emailChangeSet(email: Option[NotificationEmail]) = {
    Seq(
      (EmailAddressSearchKey -> email.flatMap(_.address)),
      (EmailTimestampSearchKey -> email.flatMap(_.timestamp))
    ).collect { case (field, Some(value)) => (field -> toJsFieldJsValueWrapper(value)) }
  }

  private def eoriPeriodChangeSet(eoriPeriod: EoriPeriod) = {
    Seq(
      FieldEori -> Some(eoriPeriod.eori),
      FieldEoriValidFrom -> eoriPeriod.validFrom,
      FieldEoriValidUntil -> eoriPeriod.validUntil
    ).collect { case (field, Some(value)) => (field -> toJsFieldJsValueWrapper(value))}
  }

  private def updateElementInEoriHistory(eoriPeriod: EoriPeriod) = {
    val eoriPeriodInEoryHistory = eoriPeriodChangeSet(eoriPeriod).map(eoriPeriodField => (FieldEoriHistory + ".$[x]." + eoriPeriodField._1 -> eoriPeriodField._2))
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriod.eori),
      update = Json.obj("$set" -> Json.obj(eoriPeriodInEoryHistory: _*)),
      upsert = true,
      arrayFilters = Seq(Json.obj(s"x.$FieldEori" -> eoriPeriod.eori))
    )
  }

  private def updateEmailAndLastUpdated(eori: Eori, email: Option[NotificationEmail]) = {
    val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(lastUpdatedChangeSet +: emailChangeSet(email): _*)))
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eori),
      update = Json.obj(updateSet),
      upsert = true
    )
  }

  private def insertTraderData(eoriPeriod: EoriPeriod, email: Option[NotificationEmail]) = {
    val eoriHistoryChangeSet = FieldEoriHistory -> toJsFieldJsValueWrapper(Json.arr(Json.obj(eoriPeriodChangeSet(eoriPeriod): _*)))
    val changeSet = Json.obj(lastUpdatedChangeSet +: eoriHistoryChangeSet +: emailChangeSet(email): _*)
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eoriPeriod.eori),
      update = Json.obj("$set" -> changeSet),
      upsert = true
    )
  }

  private def updateTraderData(eoriPeriod: EoriPeriod, email: Option[NotificationEmail]) = {
    for {
      _ <- updateElementInEoriHistory(eoriPeriod)
      result <- updateEmailAndLastUpdated(eoriPeriod.eori, email)
    } yield result
  }

}
