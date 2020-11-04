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

package uk.gov.hmrc.customs.datastore.services

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, ActionFilter, AnyContent, BodyParsers, Request, Result}
import uk.gov.hmrc.customs.datastore.config.AppConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServerTokenAuthorization @Inject()(
                                          appConfig: AppConfig,
                                          val parser: BodyParsers.Default
                                        )(implicit val executionContext: ExecutionContext) extends ActionBuilder[Request, AnyContent] with ActionFilter[Request] {
  override def filter[A](request: Request[A]): Future[Option[Result]] = {
    val incomingAuthHeader = request.headers.toMap.getOrElse("Authorization", Nil)
    val serverToken = appConfig.serverToken

    incomingAuthHeader.find(_ == serverToken) match {
      case Some(_) => Future.successful(None)
      case _ => Future.successful(Some(Unauthorized("Invalid server token provided")))
    }
  }
}
