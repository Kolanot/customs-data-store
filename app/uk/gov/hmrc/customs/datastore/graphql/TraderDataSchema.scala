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

package uk.gov.hmrc.customs.datastore.graphql

import javax.inject.{Inject, Singleton}
import play.api.{Logger, LoggerLike}
import sangria.macros.derive._
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput, ResultMarshaller}
import sangria.schema._
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.services.{EoriHistoryService, EoriStore}
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
class TraderDataSchema @Inject()(eoriStore: EoriStore, etmp: EoriHistoryService) extends InputUnmarshallerGenerator{

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

  def Queries()(implicit hc: HeaderCarrier): List[Field[Unit, Unit]] = List(
    Field(
      name = "byEori",
      fieldType = OptionType(TraderDataType),
      arguments = List(
        Argument("eori", StringType)
      ),
      resolve = sangriaContext => {
        val eori = sangriaContext.args.arg[String]("eori")
        val eventualTraderData = eoriStore.findByEori(eori)

        // TODO simplify this
        val isHistoricEoriLoaded = eventualTraderData.map(_.map(_.eoriHistory.headOption.exists(c => c.validFrom.isEmpty && c.validUntil.isEmpty)).getOrElse(true))
        val isHistoricEoriQueried = sangriaContext.astFields.flatMap(_.selections).map(_.renderPretty).exists(_.contains("eoriHistory"))
        isHistoricEoriLoaded.flatMap { mustRequest =>
          log.info(s"Query 'byEori' cache status - isHistoricEoriLoaded : $mustRequest , isHistoricEoriQueried: $isHistoricEoriQueried")
          (mustRequest && isHistoricEoriQueried) match {
            case true =>
              for {
                eoriHistory <- etmp.getHistory(eori)
                _ <- Future.successful(log.info("Query 'byEori' request result - EoriHistory length: " + eoriHistory.length))
                _ <- eoriStore.updateHistoricEoris(eoriHistory)
                traderData <- eoriStore.findByEori(eori)
              } yield traderData
            case false =>
              eventualTraderData
          }
        }
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
        val eventualEoriHistory = etmp.getHistory(eori.eori)

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
