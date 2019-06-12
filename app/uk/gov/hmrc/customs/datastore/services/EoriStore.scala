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
import play.api.libs.json.{Json, Writes}
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
    val InternalId = "internalId"
    val FieldEori = "eori"
    val FieldEoriHistory = "eoriHistory"
    val FieldEoriValidFrom = "validFrom"
    val FieldEoriValidUntil = "validUntil"
    val FieldEmails = "notificationEmail"
    val FieldEmailAddress = "address"
    val FieldIsValidated = "isValidated"
    val EoriSearchKey = s"$FieldEoriHistory.$FieldEori"
    val EmailSearchKey = s"$FieldEmails.$FieldEmailAddress"
    val FieldEmailIsValidated = s"$FieldEmails.$FieldIsValidated"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true),
    Index(Seq(InternalId -> IndexType.Ascending), name = Some(InternalId+"Index"), unique = true, sparse = true)
  )

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

  def getEmail(eori: Eori): Future[Option[NotificationEmail]] = {
    getTraderData(eori).map(traderData => traderData.flatMap(_.notificationEmail))
  }

  def saveEmail(eori: Eori, email: NotificationEmail): Future[Any] = {
    findAndUpdate(
      query = Json.obj(EoriSearchKey -> eori),
      update = Json.obj(
        "$setOnInsert" -> Json.obj(FieldEoriHistory -> Json.arr(Json.obj(FieldEori -> eori))),
        "$set" -> Json.obj(FieldEmails -> email)),
      upsert = true
    )
  }

  def rosmInsert(credId: InternalId, eori: Eori, email: String, isValidated: Boolean): Future[Boolean] = {
    //TODO: When someone registered for a new Eori, they will call this endpoint to save the data
    Future.successful(true)
  }

  def getByInternalId(internalId:InternalId):Future[Option[TraderData]] = {
    find(InternalId -> internalId).map(_.headOption)
  }

  /**
    * This code looks hideous. Unfortunately it seems mongo is unable to upsert objects inside an array. So I had to fall back to
    * 1. Query the document to decide if the there is an object with the given Eori inside the array
    *  a. If the eori is there already, we update the array at the given position
    *  b. If the eori is not there , we add it to the collection
    * And it gets even more complicated because it seems you cannot do arrayFilters (update the array at a given position) and
    * upsert other parts of the document at the same time, so in this case I had to split the update into 2 parts
    */
  def upsertByInternalId(internalId: InternalId, eoriPeriod:Option[EoriPeriodInput], email: Option[InputEmail]):Future[Boolean] = {
    find(InternalId -> internalId)
      .map(_.headOption.map(_.eoriHistory.map(_.eori)).getOrElse(Seq.empty))
      .flatMap{ eorisInMongo =>

        val updateEmailAddress = email.flatMap(_.address).map(address => (EmailSearchKey -> toJsFieldJsValueWrapper(address)))
        val updateEmailIsValidated = email match {
          case None => Option((FieldEmailIsValidated -> toJsFieldJsValueWrapper(false)))
          case Some(x) => Option((FieldEmailIsValidated -> toJsFieldJsValueWrapper(x.isValidated.getOrElse(false))))
        }
        val updateEmailFields =  Seq(updateEmailAddress, updateEmailIsValidated).flatten
        val eoriUpdates = eoriPeriod match {
          case Some(period) =>
            val updateEoriValidFrom = period.validFrom.map(vFrom => (FieldEoriValidFrom -> toJsFieldJsValueWrapper(vFrom)))
            val updateEoriValidUntil = period.validUntil.map(vUntil => (FieldEoriValidUntil -> toJsFieldJsValueWrapper(vUntil)))
            val updateEori = Option(FieldEori -> toJsFieldJsValueWrapper(period.eori))
            Seq(updateEori,updateEoriValidFrom ,updateEoriValidUntil).flatten
          case None =>
            Nil
        }

        val result1 = eoriPeriod match {
          case Some(newEori) => //We want to update an eori
            (eorisInMongo.contains(newEori.eori)) match {
              case true => //The Eori we want to insert already exists in the database
                val allEoriUpdates = eoriUpdates.map(eu => (FieldEoriHistory+ ".$[x]." + eu._1 -> eu._2))
                val eoriRelated = ("$set"  -> toJsFieldJsValueWrapper(Json.obj(allEoriUpdates: _*)))
                findAndUpdate(  //For some reason I am unable to submit eori updates at a position and email updates in the same Json object
                  query = Json.obj(InternalId -> internalId),
                  update = Json.obj(eoriRelated),
                  upsert = true,
                  arrayFilters = Seq(Json.obj(s"x.$FieldEori" -> newEori.eori))
                ).flatMap{ otherQuery =>
                  val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(updateEmailFields: _*)))
                  findAndUpdate(
                    query = Json.obj(InternalId -> internalId),
                    update = Json.obj(updateSet),
                    upsert = true
                  )
                }
              case false => //The Eori we want to insert is not in the database
                val eoriRelated = (FieldEoriHistory -> toJsFieldJsValueWrapper(Json.obj(eoriUpdates: _*)))
                val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(updateEmailFields: _*)))
                val eoriSet = ("$addToSet" -> toJsFieldJsValueWrapper(Json.obj(eoriRelated)))
                findAndUpdate(
                  query = Json.obj(InternalId -> internalId),
                  update = Json.obj(updateSet,eoriSet),
                  upsert = true
                )

            }
          case None => //We do not update any eori
            val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(updateEmailFields: _*)))
            val eoriField = ("$setOnInsert" -> toJsFieldJsValueWrapper(Json.obj(FieldEoriHistory -> Json.arr())))
            findAndUpdate(
              query = Json.obj(InternalId -> internalId),
              update = Json.obj(updateSet,eoriField),
              upsert = true
            )
        }
        result1.map(_.lastError.flatMap(_.err).isEmpty)

    }

  }


}