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

package uk.gov.hmrc.customs.datastore.testOnly.controllers

import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import uk.gov.hmrc.customs.datastore.services.FeatureSwitch
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

@Singleton
class FeatureSwitchController @Inject()(val messagesApi: MessagesApi, implicit val appConfig: AppConfig) extends FrontendController {

  def enable(featureName: String): Action[AnyContent] = Action { implicit request =>
    FeatureSwitch.forName(featureName).enable
    Ok(s"Enabled feature $featureName")
  }

  def disable(featureName: String): Action[AnyContent] = Action { implicit request =>
    FeatureSwitch.forName(featureName).disable
    Ok(s"Disabled feature $featureName")
  }

}