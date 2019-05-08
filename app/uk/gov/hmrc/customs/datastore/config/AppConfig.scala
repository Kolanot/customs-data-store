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

package uk.gov.hmrc.customs.datastore.config

import javax.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, val environment: Environment) extends ServicesConfig {
  override protected def mode: Mode = environment.mode

  val mdg = baseUrl("mdg") /  getConfString("mdg.context","customs-financials-hods-stub")
  val eoriHistoryUrl = mdg / getConfString("mdg.sub21","eorihistory")


  implicit class URLLike(left:String){
    def /(right:String):String = checkEnding(left) + "/" + checkBeginning(right)
    def checkEnding(in:String):String = if (in.lastIndexOf("/") == in.size - 1) in.take(in.size-1) else in
    def checkBeginning(in:String):String = if (in.indexOf("/") == 0) in.drop(1) else in
  }

}


