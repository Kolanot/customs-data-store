/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.running
import uk.gov.hmrc.customs.datastore.utils.SpecBase

class ServerTokenAuthorizationSpec extends SpecBase {

  "ServerTokenAuthorization" should {
    "Reject if no Authorization header was provided" in  {

      val app = new GuiceApplicationBuilder().build()
      val service = app.injector.instanceOf[ServerTokenAuthorization]

      running(app) {
        val req = FakeRequest()
        val result = await(service.filter(req))
        result.get.header.status  mustBe UNAUTHORIZED
      }
    }

    "Reject invalid Authorization token" in {

      val app = new GuiceApplicationBuilder().build()
      val service = app.injector.instanceOf[ServerTokenAuthorization]

      running (app) {
        val req = FakeRequest().withHeaders("Authorization" -> "invalid")
        val result = await(service.filter(req))
        result.get.header.status mustBe UNAUTHORIZED
      }
    }

    "Accept valid Authorization token" in {

      val app = new GuiceApplicationBuilder().build()
      val service = app.injector.instanceOf[ServerTokenAuthorization]

      running (app) {
        val req = FakeRequest().withHeaders("Authorization" -> "Bearer secret-token")
        val result = await(service.filter(req))
        result mustBe None
      }
    }
  }
}
