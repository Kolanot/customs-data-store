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
    val FieldEori = "eori"  //classOf[EoriPeriod].getDeclaredFields.apply(0).getName
    val FieldEoriHistory = "eoriHistory"  //classOf[TraderData].getDeclaredFields.apply(1).getName
    val FieldEmails = "notificationEmail"//classOf[TraderData].getDeclaredFields.apply(2).getName
    val FieldEmailAddress = "address"//classOf[NotificationEmail].getDeclaredFields.apply(0).getName
    val EoriSearchKey = s"$FieldEoriHistory.$FieldEori"
    val EmailSearchKey = s"$FieldEmails.$FieldEmailAddress"
    val FieldIsValidated = s"$FieldEmails.${classOf[NotificationEmail].getDeclaredFields.apply(1).getName}"
    //val FieldEoriNumber = s"$FieldEoriHistory.${classOf[EoriPeriod].getDeclaredFields.apply(0).getName}"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(EoriSearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true),
    //Index(Seq(EmailSearchKey -> IndexType.Ascending), name = Some(FieldEmails + FieldEmailAddress + "Index"), unique = true, sparse = true),
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
    println("### getByInternalId: " + internalId)
    find(InternalId -> internalId).map(_.headOption)
  }

  def upsertByInternalId(internalId: InternalId, eoriPeriod:Option[EoriPeriodInput], email: Option[InputEmail]):Future[Boolean] = {
    println("###upsertByInternalId: " + internalId + " ### " + email)
    val updateEmailAddress = email.flatMap(_.address).map(address => (EmailSearchKey -> toJsFieldJsValueWrapper(address)))
    val updateEmailIsValidated = email match {
      case None => Option((FieldIsValidated -> toJsFieldJsValueWrapper(false)))
      case Some(x) => Option((FieldIsValidated -> toJsFieldJsValueWrapper(x.isValidated.getOrElse(false))))  //x.isValidated.getOrElse(false).map(isValidated =>
    }
    val updateEoriNumber = eoriPeriod match {
      case Some(period) =>
        Option(FieldEoriHistory -> toJsFieldJsValueWrapper (Json.arr (Json.obj (FieldEori -> period.eori) ) ) )
      case None =>
        Option( FieldEoriHistory -> toJsFieldJsValueWrapper (Json.arr () ) )
    }
    val updateFields =  Seq(updateEmailAddress, updateEmailIsValidated, updateEoriNumber).flatten
    val updateSet = ("$set" -> toJsFieldJsValueWrapper(Json.obj(updateFields: _*)))
    println("###updateSet: " + updateSet)
    findAndUpdate(
      query = Json.obj(InternalId -> internalId),
      update = Json.obj(updateSet),
      upsert = true
    ).map(_.lastError.flatMap(_.err).isEmpty)
  }


}