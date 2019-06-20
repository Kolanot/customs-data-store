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

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.mvc.{ActionBuilder, ActionFilter, Request, Result}
import uk.gov.hmrc.customs.datastore.config.AppConfig
import play.api.mvc.Results._

import scala.concurrent.Future

@Singleton
class ServerTokenAuthorization @Inject()(appConfig: AppConfig) extends ActionBuilder[Request] with ActionFilter[Request] {
  override def filter[A](request: Request[A]): Future[Option[Result]] = {
    val incomingAuthHeader = request.headers.get("Authorization")
    val authToken = appConfig.authToken
    incomingAuthHeader.map(_ == authToken) match {
      case Some(true) => Future.successful(None)
      case _ => Future.successful(Some(Unauthorized("Please provide valid auth token to access this endpoint")))
    }
  }
}
