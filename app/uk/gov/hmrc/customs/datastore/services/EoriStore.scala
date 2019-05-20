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
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.customs.datastore.domain.{EoriHistory, TraderData}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.customs.datastore.domain.TraderData._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class EoriStore  @Inject()(mongoComponent: ReactiveMongoComponent)
  extends {
    val EoriFieldsName = classOf[EoriHistory].getDeclaredFields.apply(0).getName
    val EorisFieldsName = classOf[TraderData].getDeclaredFields.apply(1).getName
    val EoriSearchField = s"$EorisFieldsName.$EoriFieldsName"
  }
    with ReactiveRepository[TraderData, BSONObjectID](
    collectionName = "dataStore",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = TraderData.traderDataFormat,
    idFormat = ReactiveMongoFormats.objectIdFormats
  ) {


  override def indexes: Seq[Index] = Seq(
    Index(Seq(EoriSearchField -> IndexType.Ascending), name = Some(EorisFieldsName + EoriFieldsName + "Index"), unique = true, sparse = true))

  def saveEoris(histories:Seq[EoriHistory]):Future[Any] = {
    findAndUpdate(
      query = Json.obj(EoriSearchField -> Json.obj("$in" -> histories.map(_.eori))),
      update = Json.obj(EorisFieldsName -> Json.toJson(histories)),
      upsert = true
    )
  }

  def getEori(eori: String): Future[Option[TraderData]] = {
    find(EoriSearchField -> eori).map(_.headOption)
  }

}