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

import org.scalatest.{FlatSpec, MustMatchers}
import play.api.{Configuration, Environment}

class AppConfigSpec extends FlatSpec with MustMatchers {

  class AppConfigScenario {
    val env = Environment.simple()
    val configuration = Configuration.load(env)
  }

  it should "remove left hand side slash" in new AppConfigScenario {
    val appConfig = new AppConfig(configuration,env) {
      val url = "http://localhost/" / "abcd"
      url mustBe "http://localhost/abcd"
    }
  }

  it should "remove right hand side slash" in new AppConfigScenario {
    val appConfig = new AppConfig(configuration,env) {
      val url = "http://localhost" / "/abcd"
      url mustBe "http://localhost/abcd"
    }
  }

  it should "remove left and right hand side slashes" in new AppConfigScenario {
    val appConfig = new AppConfig(configuration,env) {
      val url = "http://localhost/" / "/abcd"
      url mustBe "http://localhost/abcd"
    }
  }

  it should "No slashes" in new AppConfigScenario {
    val appConfig = new AppConfig(configuration,env) {
      val url = "http://localhost" / "abcd"
      url mustBe "http://localhost/abcd"
    }
  }


}
