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
import sangria.macros.derive._
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput, ResultMarshaller}
import sangria.schema._
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.services.{ETMPHistoryService, EoriStore}
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
class TraderDataSchema @Inject()(eoriStore: EoriStore,etmp: ETMPHistoryService) extends InputUnmarshallerGenerator{

  val NotificationEmail = "notificationEmail"
  val Address = "address"
  val FieldTimestamp = "timestamp"
  val EoriField = "eoriHistory"

  implicit val EoriHistoryType: ObjectType[Unit, EoriPeriod] = deriveObjectType[Unit, EoriPeriod](ObjectTypeName("EoriHistory"))
  implicit val EmailType: ObjectType[Unit, NotificationEmail] = deriveObjectType[Unit, NotificationEmail](ObjectTypeName("Email"))
  implicit val TraderDataType: ObjectType[Unit, TraderData] = deriveObjectType[Unit, TraderData](ObjectTypeName("TraderData"))

  implicit val InputEmailType:InputObjectType[InputEmail] = deriveInputObjectType[InputEmail]()
  implicit val InputEmailUnmarshaller: FromInput[InputEmail] = inputUnmarshaller {
    input => InputEmail(
      address = input.get(Address).flatMap(_.asInstanceOf[Option[EmailAddress]]),
      timestamp = input.get(FieldTimestamp).flatMap(_.asInstanceOf[Option[Timestamp]])
    )
  }
  implicit val InputEoriPeriodType:InputObjectType[EoriPeriodInput] = deriveInputObjectType[EoriPeriodInput]()
  implicit val InputEoriPeriodTypeUnmarshaller = inputUnmarshaller({
    input => EoriPeriodInput(
      eori = input("eori").asInstanceOf[Eori],
      validFrom = input.get("validFrom").flatMap(_.asInstanceOf[Option[String]]),
      validUntil = input.get("validUntil").flatMap(_.asInstanceOf[Option[String]])
    )
  })


  val Queries: List[Field[Unit, Unit]] = List(
    Field(
      name = "byEori",
      fieldType = OptionType(TraderDataType),
      arguments = List(
        Argument("eori", StringType)
      ),
      resolve = sangriaContext => {
        implicit val hc = HeaderCarrier()
        val eori = sangriaContext.args.arg[String]("eori")
        val eventualTradarData = eoriStore.findByEori(eori)

        val mustRequestHistoricEori = eventualTradarData.map( a => a.map(b => b.eoriHistory.headOption.find( c => c.validFrom.isEmpty && c.validUntil.isEmpty).isDefined ).getOrElse(false))

        mustRequestHistoricEori.flatMap { mustRequest =>
          mustRequest match {
            case true =>
              for {
                eoriHistory <- etmp.getHistory(eori)
                _ <- eoriStore.saveEoris(eoriHistory)
                traderData <- eoriStore.findByEori(eori)
              } yield traderData  //eoriStore.findByEori(eori)
            case false =>
              eventualTradarData
          }
        }
      }
    )
  )

  /**
    * List of mutations to work with the entity of TraderData.
    */
  val Mutations: List[Field[Unit, Unit]] = List(
    //Example: {"query" : "mutation {addTrader(eori:\"GB12345678\" notificationEmail:\"abc@goodmail.com\")}" }
    Field(
      name = "byEori",
      fieldType = BooleanType,
      arguments = List(
        Argument(EoriField, InputEoriPeriodType),
        Argument(NotificationEmail, OptionInputType(InputEmailType))
      ),
      resolve = ctx => {
        val email = ctx.args.raw.get(NotificationEmail).flatMap(_.asInstanceOf[Option[InputEmail]])
        val eori = ctx.args.raw(EoriField).asInstanceOf[EoriPeriodInput]
        implicit val hc = HeaderCarrier()
        val eventualEoriHistory = etmp.getHistory(eori.eori)

        for {
          result <- eoriStore.upsertByEori(eori,email)
          eoriHistory <- eventualEoriHistory
          _ <- eoriStore.saveEoris(eoriHistory)
        } yield result
      }
    )
  )

}
