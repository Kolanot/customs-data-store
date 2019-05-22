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

import java.security.InvalidParameterException

import javax.inject._
import play.api.libs.json.Json._
import play.api.libs.json.{JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.customs.datastore.domain.TraderData._
import uk.gov.hmrc.customs.datastore.domain.{Email, EmailAddress, Eori, EoriPeriod, TraderData}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class EoriStore  @Inject()(mongoComponent: ReactiveMongoComponent)
  extends {
    val FieldEori = classOf[EoriPeriod].getDeclaredFields.apply(0).getName
    val FieldEoriHistory = classOf[TraderData].getDeclaredFields.apply(1).getName
    val FieldEmails = classOf[TraderData].getDeclaredFields.apply(2).getName
    val SearchKey = s"$FieldEoriHistory.$FieldEori"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(SearchKey -> IndexType.Ascending), name = Some(FieldEoriHistory + FieldEori + "Index"), unique = true, sparse = true))

  def saveEoris(eoriHistory:Seq[EoriPeriod]):Future[Any] = {
    findAndUpdate(
      query = Json.obj(SearchKey -> Json.obj("$in" -> eoriHistory.map(_.eori))),
      update = Json.obj("$setOnInsert" -> defaultsWithout(FieldEoriHistory), "$set" -> Json.obj(FieldEoriHistory -> Json.toJson(eoriHistory))),
      upsert = true
    )
  }

  def getEori(eori: String): Future[Option[TraderData]] = {
    find(SearchKey -> eori).map(_.headOption)
  }

  def defaultsWithout(field: String) = field match {
    case FieldEoriHistory => Json.obj(FieldEmails -> Json.arr())
    case FieldEmails => Json.obj(FieldEoriHistory -> Json.arr())
    case _ => throw new InvalidParameterException(s"unknown field: $field")
  }

  def getEmail(eori: Eori): Future[Seq[EmailAddress]] = {
    find(FieldEori -> eori).map(_.headOption).map(traderData => traderData.map(_.emails.map(_.address)).getOrElse(Nil))
  }

  def saveEmail(eori: Eori, email: EmailAddress): Future[Any] = {
    findAndUpdate(
      query = Json.obj(SearchKey -> eori),
      update = Json.obj(
        "$setOnInsert" -> defaultsWithout(FieldEmails),
        "$set" -> Json.obj(FieldEori -> eori),
        "$addToSet" -> Json.obj(FieldEmails -> Email(email, false))),
      upsert = true
    )
  }

}