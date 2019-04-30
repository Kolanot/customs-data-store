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

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import play.api.mvc._
import uk.gov.hmrc.customs.datastore.services.EoriStore
import play.api.libs.json.Json
import uk.gov.hmrc.customs.datastore.domain.EoriHistory._

@Singleton()
class HistoricEoriController @Inject()(eoriStore: EoriStore)(implicit ec: ExecutionContext) extends BaseController {

	def getEoriHistory(eori: String): Action[AnyContent] = Action.async { implicit request =>
		eoriStore.getEori(eori).map(maybeEoriHistory => Ok(Json.toJson(maybeEoriHistory)))
	}

}
