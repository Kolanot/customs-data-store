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

package uk.gov.hmrc.customs.datastore.graphql

import javax.inject.{Inject, Singleton}
import play.api.{Logger, LoggerLike}
import sangria.macros.derive._
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput, ResultMarshaller}
import sangria.schema._
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.services.{EoriHistoryService, EoriStore, SubscriptionInfoService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait InputUnmarshallerGenerator {

  def inputUnmarshaller[T](inputToClassFunc: Map[String, Any] => T): FromInput[T] = new FromInput[T] {
    val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

    override def fromResult(node: marshaller.Node): T = inputToClassFunc(node.asInstanceOf[Map[String, Any]])
  }
}

@Singleton
class TraderDataSchema @Inject()(eoriStore: EoriStore,
                                 historyService: EoriHistoryService,
                                 subscriptionInfoService:SubscriptionInfoService) extends InputUnmarshallerGenerator {

  val log: LoggerLike = Logger(this.getClass)

  val FieldNotificationEmail = "notificationEmail"
  val Address = "address"
  val FieldTimestamp = "timestamp"
  val EoriField = "eoriHistory"

  implicit val EoriHistoryType: ObjectType[Unit, EoriPeriod] = deriveObjectType[Unit, EoriPeriod](ObjectTypeName("EoriHistory"))
  implicit val EmailType: ObjectType[Unit, NotificationEmail] = deriveObjectType[Unit, NotificationEmail](ObjectTypeName("Email"))
  implicit val TraderDataType: ObjectType[Unit, TraderData] = deriveObjectType[Unit, TraderData](ObjectTypeName("TraderData"))

  implicit val InputEmailType:InputObjectType[NotificationEmail] = deriveInputObjectType[NotificationEmail]()
  implicit val InputEmailUnmarshaller: FromInput[NotificationEmail] = inputUnmarshaller {
    input => NotificationEmail(
      address = input.get(Address).flatMap(_.asInstanceOf[Option[EmailAddress]]),
      timestamp = input.get(FieldTimestamp).flatMap(_.asInstanceOf[Option[Timestamp]])
    )
  }
  implicit val InputEoriPeriodType:InputObjectType[EoriPeriod] = deriveInputObjectType[EoriPeriod]()
  implicit val InputEoriPeriodTypeUnmarshaller = inputUnmarshaller({
    input => EoriPeriod(
      eori = input("eori").asInstanceOf[Eori],
      validFrom = input.get("validFrom").flatMap(_.asInstanceOf[Option[String]]),
      validUntil = input.get("validUntil").flatMap(_.asInstanceOf[Option[String]])
    )
  })

  def retrieveAndStoreHistoricEoris(eori: Eori)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      eoriHistory <- historyService.getHistory(eori)
      result <- eoriStore.updateHistoricEoris(eoriHistory)
    } yield result
  }

  def retrieveAndStoreCustomerInformation(eori: Eori)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      customerInfo <-subscriptionInfoService.getSubscriberInformation(eori)
      result <- eoriStore.upsertByEori(EoriPeriod(eori,None,None), customerInfo.map(ci => NotificationEmail(ci.emailAddress,ci.verifiedTimestamp)))
    } yield result
  }


  def Queries()(implicit hc: HeaderCarrier): List[Field[Unit, Unit]] = List(
    Field(
      name = "byEori",
      fieldType = OptionType(TraderDataType),
      arguments = List(
        Argument("eori", StringType)
      ),
      resolve = sangriaContext => {
        val eori = sangriaContext.args.arg[String]("eori")
        for {
          maybeTraderData <- eoriStore.findByEori(eori)
          isHistoricEoriStored = maybeTraderData.map(_.eoriHistory.headOption.exists(c => c.validFrom.isDefined || c.validUntil.isDefined)).getOrElse(false)
          isTraderEmailStored = maybeTraderData.flatMap(_.notificationEmail).map(_.address.isDefined).getOrElse(false)
          _ <- if (isHistoricEoriStored) Future.successful(true) else retrieveAndStoreHistoricEoris(eori)
          _ <- if (isTraderEmailStored) Future.successful(true) else retrieveAndStoreCustomerInformation(eori)
          traderData <- if (isHistoricEoriStored && isTraderEmailStored) Future.successful(maybeTraderData ) else eoriStore.findByEori(eori)  //Retrieve again only if there were updates
        } yield traderData

      }
    ),
    Field(
      name = "getEmail",
      fieldType = OptionType(EmailType),
      arguments = List(
        Argument("eori", StringType)
      ),
      resolve = sangriaContext => {
        val eori = sangriaContext.args.arg[String]("eori")
        for {
          maybeEmailData <- eoriStore.findByEori(eori).map { case Some(traderData) => traderData.notificationEmail }
          isTraderEmailStored = maybeEmailData.isDefined
          _ <- if (isTraderEmailStored) Future.successful(true) else retrieveAndStoreCustomerInformation(eori)
          emailData <- if (isTraderEmailStored) Future.successful(maybeEmailData) else eoriStore.findByEori(eori).map { case Some(traderData) => traderData.notificationEmail }
        } yield emailData
      }
    )
  )

  /**
    * List of mutations to work with the entity of TraderData.
    */
  def Mutations()(implicit hc: HeaderCarrier): List[Field[Unit, Unit]] = List(
    Field(
      name = "byEori",
      fieldType = BooleanType,
      arguments = List(
        Argument(EoriField, InputEoriPeriodType),
        Argument(FieldNotificationEmail, OptionInputType(InputEmailType))
      ),
      resolve = ctx => {
        val email = ctx.args.raw.get(FieldNotificationEmail).flatMap(_.asInstanceOf[Option[NotificationEmail]])
        val eori = ctx.args.raw(EoriField).asInstanceOf[EoriPeriod]
        val eventualEoriHistory = historyService.getHistory(eori.eori)

        for {
          result <- eoriStore.upsertByEori(eori,email)
          _ <- Future.successful(log.info(s"Mutation 'byEori' request result: $result"))
          eoriHistory <- eventualEoriHistory
          _ <- eoriStore.updateHistoricEoris(eoriHistory)
        } yield result
      }
    )
  )
}
