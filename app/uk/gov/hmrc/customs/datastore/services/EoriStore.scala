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

@Singleton
class EoriStore @Inject()(mongoComponent: ReactiveMongoComponent, appConfig:AppConfig)
  extends {
    protected val FieldEoriHistory = "eoriHistory"
    protected val FieldEori = "eori"
    protected val EoriSearchKey = s"$FieldEoriHistory.$FieldEori"
    protected val FieldEoriValidFrom = "validFrom"
    protected val FieldEoriValidUntil = "validUntil"
    protected val FieldEmails = "notificationEmail"
    protected val FieldEmailAddress = "address"
    protected val FieldTimestamp = "timestamp"
    protected val EmailAddressSearchKey = s"$FieldEmails.$FieldEmailAddress"
    protected val EmailTimestampSearchKey = s"$FieldEmails.$FieldTimestamp"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) with TTLIndexing[TraderData, BSONObjectID] {

  override val expireAfterSeconds: Long = appConfig.dbTimeToLive

  override def additionalIndexes = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true)
  )

  def findByEori(eori: Eori): Future[Option[TraderData]] = {
    val selector = BSONDocument(EoriSearchKey -> eori)
    find(EoriSearchKey -> eori).map(_.headOption)
  }

  def getLastUpdated() = LastUpdated -> toJsFieldJsValueWrapper(DateTime.now(DateTimeZone.UTC))(ReactiveMongoFormats.dateTimeWrite)

  /*
  This method will overwrite the eoriHistory field, with the given EoriPeriods but leaves the other fields untouched
   */
  def updateHistoricEoris(eoriHistories: Seq[EoriPeriod]): Future[Any] = {
    val eoriHistoryJson = FieldEoriHistory -> toJsFieldJsValueWrapper(eoriHistories)
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> Json.obj("$in" -> eoriHistories.map(_.eori))),
      update = Json.obj("$set" -> Json.obj(getLastUpdated, eoriHistoryJson)),
      upsert = true
    )
  }

  def upsertByEori(eoriPeriodInput: EoriPeriodInput, email: Option[InputEmail]): Future[Boolean] = {
    val updateEmailAddress = email.flatMap(_.address).map(address => (EmailAddressSearchKey -> toJsFieldJsValueWrapper(address)))
    val updateEmailTimestamp = email.flatMap(_.timestamp).map(timestamp => (EmailTimestampSearchKey -> toJsFieldJsValueWrapper(timestamp)))
    val updateEmailFields = Seq(updateEmailAddress, updateEmailTimestamp).flatten

    val updateEoriValidFrom = eoriPeriodInput.validFrom.map(vFrom => (FieldEoriValidFrom -> toJsFieldJsValueWrapper(vFrom)))
    val updateEoriValidUntil = eoriPeriodInput.validUntil.map(vUntil => (FieldEoriValidUntil -> toJsFieldJsValueWrapper(vUntil)))
    val updateEori = Option(FieldEori -> toJsFieldJsValueWrapper(eoriPeriodInput.eori))
    val updateHistoricEoriFields = Seq(updateEoriValidFrom, updateEoriValidUntil, updateEori).flatten


    findByEori(eoriPeriodInput.eori)
        .flatMap{maybeTraderData =>
          maybeTraderData match {
            case Some(traderData) => //The TraderData already exists (based on the EORI), we have to update it
              val allEoriUpdates = updateHistoricEoriFields.map(eu => (FieldEoriHistory + ".$[x]." + eu._1 -> eu._2))
              val eoriRelated = ("$set"  -> toJsFieldJsValueWrapper(Json.obj(allEoriUpdates: _*)))
              findAndUpdate(  //For some reason I am unable to submit eori updates at a position and email updates in the same Json object
                query = Json.obj(EoriSearchKey -> eoriPeriodInput.eori),
                update = Json.obj(eoriRelated),
                upsert = true,
                arrayFilters = Seq(Json.obj(s"x.$FieldEori" -> eoriPeriodInput.eori))
              ).flatMap{ otherQuery =>
                val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(getLastUpdated +: updateEmailFields: _*)))
                findAndUpdate(
                  query = Json.obj(EoriSearchKey -> eoriPeriodInput.eori),
                  update = Json.obj(updateSet),
                  upsert = true
                )
              }
            case None =>  //The TraderData (based on the EORI) doesn't exist, so we have to insert it
              val eoriUpdates = FieldEoriHistory -> toJsFieldJsValueWrapper(Json.arr(Json.obj(updateHistoricEoriFields: _*)))
              val allUpdates = Json.obj(getLastUpdated +: eoriUpdates +: updateEmailFields : _*)
              findAndUpdate(
                query = Json.obj(EoriSearchKey -> eoriPeriodInput.eori),
                update = Json.obj("$set" -> toJsFieldJsValueWrapper(allUpdates)),
                upsert = true
              )

          }
        }
      .map(_.lastError.flatMap(_.err).isEmpty)
  }

}

