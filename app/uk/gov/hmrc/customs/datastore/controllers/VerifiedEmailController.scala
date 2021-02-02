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

package uk.gov.hmrc.customs.datastore.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.customs.datastore.domain.request.UpdateVerifiedEmailRequest
import uk.gov.hmrc.customs.datastore.domain.{Eori, EoriPeriod, NotificationEmail}
import uk.gov.hmrc.customs.datastore.services.{EoriStore, SubscriptionInfoService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VerifiedEmailController @Inject()(
                                         eoriStore: EoriStore,
                                         subscriptionInfoService: SubscriptionInfoService,
                                         cc: ControllerComponents
                                       )(implicit executionContext: ExecutionContext) extends BackendController(cc) {

  def getVerifiedEmail(eori: String): Action[AnyContent] = Action.async { implicit request =>
    val emailData = for {
      maybeEmailData <- eoriStore.findByEori(eori).map {
        case Some(traderData) => traderData.notificationEmail
        case None => None
      }
      emailData <- if (maybeEmailData.isDefined) Future.successful(maybeEmailData) else retrieveAndStoreCustomerInformation(eori)
    } yield emailData

    emailData.map {
      case Some(emailData) => Ok(Json.toJson(emailData))
      case None => NotFound
    }.recover { case _ => InternalServerError }
  }

  def updateVerifiedEmail(): Action[UpdateVerifiedEmailRequest] = Action.async(parse.json[UpdateVerifiedEmailRequest]) { implicit request =>
    eoriStore.upsertByEori(
      EoriPeriod(request.body.eori, None, None),
      Some(NotificationEmail.fromEmailRequest(request.body))
    ).map { updateSucceeded => if (updateSucceeded) NoContent else InternalServerError }
  }

  private def retrieveAndStoreCustomerInformation(eori: Eori)(implicit hc: HeaderCarrier): Future[Option[NotificationEmail]] = {
    subscriptionInfoService.getSubscriberInformation(eori).flatMap {
      case None => Future.successful(None)
      case Some(sub09DataModel) =>
        eoriStore.upsertByEori(EoriPeriod(eori, None, None), Some(NotificationEmail(sub09DataModel.emailAddress, sub09DataModel.verifiedTimestamp))).flatMap { writeSucceeded =>
          if (writeSucceeded) {
            eoriStore.findByEori(eori).map {
              case Some(traderData) => traderData.notificationEmail
              case None => throw new RuntimeException("Failed to retrieve email after updating cache")
            }
          } else {
            throw new RuntimeException("Failed to to update cache")
          }
        }
    }
  }
}
