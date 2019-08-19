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

import com.google.inject.AbstractModule
import javax.inject.{Inject,Singleton}
import play.api.{Logger, LoggerLike}
import play.api.inject.ApplicationLifecycle

/**
  * Enable this class in application.conf if you want to use it:
  * play.modules.enabled += "uk.gov.hmrc.customs.datastore.config.StartModule"
  */
class StartModule extends AbstractModule {
  override def configure() = {
    bind(classOf[ApplicationStart]).asEagerSingleton()
  }
}

@Singleton
class ApplicationStart @Inject()(lifecycle: ApplicationLifecycle) {
  val log: LoggerLike = Logger(this.getClass)
  log.debug("GraphQL ApplicationStart DEBUG")
  log.info("GraphQL ApplicationStart INFO")
  log.warn("GraphQL ApplicationStart WARN")
  log.error("GraphQL ApplicationStart ERROR")

}
