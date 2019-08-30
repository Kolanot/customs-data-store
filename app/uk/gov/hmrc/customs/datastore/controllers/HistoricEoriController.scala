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
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.customs.datastore.services.{ETMPService, EoriStore}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext

@Singleton()
class HistoricEoriController @Inject()(val authConnector: CustomAuthConnector, eoriStore: EoriStore, etmp: ETMPService)(implicit ec: ExecutionContext) extends BaseController with AuthorisedFunctions {

  def mdgHistoricEori(eori: String) = Action.async { implicit req =>
    etmp.testSub21(eori).map(a => Ok(a))
  }

  def mdgGetEmail(eori: String) = Action.async { implicit req =>
    etmp.testSub09(eori).map(a => Ok(a))
  }

}
