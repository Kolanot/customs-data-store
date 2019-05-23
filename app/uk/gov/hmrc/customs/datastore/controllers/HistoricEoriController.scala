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

package uk.gov.hmrc.customs.datastore.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.customs.datastore.domain.TraderData
import uk.gov.hmrc.customs.datastore.domain.TraderData._
import uk.gov.hmrc.customs.datastore.services.{ETMPHistoryService, EoriStore}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class HistoricEoriController @Inject()(eoriStore: EoriStore, etmp: ETMPHistoryService)(implicit ec: ExecutionContext) extends BaseController {

  def getEoriHistory(eori: String): Action[AnyContent] = Action.async { implicit request =>

    eoriStore.getTraderDate(eori).flatMap {
      case None =>
        etmp.getHistory(eori)
          .map { eoriPeriods =>
            //eoriStore.saveEoris(eoriPeriods)
            eoriStore.insert(TraderData(None, eoriPeriods, Nil))
            eoriPeriods
          }
      case Some(traderData) =>
        Future.successful(traderData.eoriHistory)
    }
    .map(history => Ok(Json.toJson(history)))

  }

}
