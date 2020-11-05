/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.Configuration
import uk.gov.hmrc.customs.datastore.services.FeatureSwitch
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject()(val configuration: Configuration, servicesConfig: ServicesConfig){

  val authUrl = servicesConfig.baseUrl("auth")

  val serverToken = "Bearer " + configuration.get[String]("server-token")
  val bearerToken = "Bearer " + configuration.getOptional[String]("microservice.services.actualmdg.bearer-token").getOrElse("secret-token")

  def eoriHistoryUrl: String = FeatureSwitch.ActualMdg.isEnabled() match {
    case true => servicesConfig.baseUrl("actualmdg") / configuration.get[String]("microservice.services.actualmdg.historicEoriEndpoint")
    case false => servicesConfig.baseUrl("mdg") / configuration.get[String]("microservice.services.mdg.historicEoriEndpoint")
  }

  // TODO: rename actualmdg -> mdg; mdg -> mdg-stub
  def companyInformationUrl: String = FeatureSwitch.ActualMdg.isEnabled() match {
    case true => servicesConfig.baseUrl("actualmdg") / configuration.get[String]("microservice.services.actualmdg.companyInformationEndpoint")
    case false => servicesConfig.baseUrl("mdg") / configuration.get[String]("microservice.services.mdg.companyInformationEndpoint")
  }

  private val DEFAULT_TIME_TO_LIVE: Int = 30 * 24 * 60 * 60
  val dbTimeToLiveInSeconds: Int = configuration.getOptional[Int]("mongodb.timeToLiveInSeconds").getOrElse(DEFAULT_TIME_TO_LIVE)

  //Remove duplicate / from urls read from config
  implicit class URLSyntacticSugar(left: String) {
    def /(right: String): String = removeTrailingSlash(left) + "/" + removeLeadingSlash(right)

    def removeTrailingSlash(in: String): String = if (in.last == '/') in.dropRight(1) else in

    def removeLeadingSlash(in: String): String = if (in.head == '/') in.drop(1) else in
  }

}


