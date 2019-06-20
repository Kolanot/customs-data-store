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

import org.scalatest.{MustMatchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.{Configuration, Environment, http}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import play.api.http.Status._

class ServerTokenAuthorizationSpec extends WordSpec with MustMatchers with FutureAwaits with DefaultAwaitTimeout{

  class ServerTokenAuthorizationScenario() {
    val env = Environment.simple()
    val configuration = Configuration.load(env)
    implicit val appConfig = new AppConfig(configuration, env)

    val service = new ServerTokenAuthorization(appConfig)
  }

  "ServerTokenAuthorization" should {
    "Reject if no Authorization header was provided" in new ServerTokenAuthorizationScenario {
      val req = FakeRequest()
      val result = await(service.filter(req))
      result.get.header.status  mustBe UNAUTHORIZED
    }

    "Reject invalid Authorization token" in new ServerTokenAuthorizationScenario {
      val req = FakeRequest().withHeaders("Authorization" -> "invalid")
      val result = await(service.filter(req))
      result.get.header.status  mustBe UNAUTHORIZED
    }

    "Accept valid Authorization token" in new ServerTokenAuthorizationScenario {
      val req = FakeRequest().withHeaders("Authorization" -> "secret-token")
      val result = await(service.filter(req))
      result mustBe None
    }


  }
}