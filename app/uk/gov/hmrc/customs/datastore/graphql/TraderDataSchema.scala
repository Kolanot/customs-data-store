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
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.{Logger, LoggerLike}
import sangria.ast.StringValue
import sangria.macros.derive._
import sangria.marshalling.{CoercedScalaResultMarshaller, DateSupport, FromInput, ResultMarshaller}
import sangria.schema._
import sangria.validation.ValueCoercionViolation
import uk.gov.hmrc.customs.datastore.domain._
import uk.gov.hmrc.customs.datastore.services.{ETMPHistoryService, EoriStore}
import uk.gov.hmrc.http.HeaderCarrier

import scala.util.{Failure, Success, Try}
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

  val log: LoggerLike = Logger(this.getClass)

  val NotificationEmail = "notificationEmail"
  val Address = "address"
  val FieldTimestamp = "timestamp"
  val EoriField = "eoriHistory"

  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")

  def parseDate(s: String) = Try(new DateTime(s, DateTimeZone.UTC)) match {
    case Success(date) ⇒ Right(date)
    case Failure(_) ⇒ Left(DateCoercionViolation)
  }
  implicit val DateTimeType = ScalarType[DateTime]("DateTime",
    coerceOutput = (d, caps) ⇒
      if (caps.contains(DateSupport)) d.toDate
      else ISODateTimeFormat.dateTime().print(d),
    coerceUserInput = {
      case s: String ⇒ parseDate(s)
      case _ ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case StringValue(s, _, _,_,_) ⇒ parseDate(s)
      case _ ⇒ Left(DateCoercionViolation)
    })

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

        val isHisricEoriLoaded = eventualTradarData.map( a => a.map(b => b.eoriHistory.headOption.find( c => c.validFrom.isEmpty && c.validUntil.isEmpty).isDefined ).getOrElse(true))
        val isHistoricEoriQueried = sangriaContext.astFields.flatMap(_.selections).map(_.renderPretty).find(_.contains("eoriHistory")).isDefined
        isHisricEoriLoaded.flatMap { mustRequest =>
          log.warn(s"Query 'byEori' cache status - isHisricEoriLoaded : $mustRequest , isHistoricEoriQueried: $isHistoricEoriQueried")
          (mustRequest && isHistoricEoriQueried) match {
            case true =>
              for {
                eoriHistory <- etmp.getHistory(eori)
                _ <- Future.successful(log.warn("Query 'byEori' request result - EoriHistory length: " + eoriHistory.length))
                _ <- eoriStore.saveEoris(eoriHistory)
                traderData <- eoriStore.findByEori(eori)
              } yield traderData
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
          _ <- Future.successful(log.warn(s"Mutation 'byEori' request result: $result"))
          eoriHistory <- eventualEoriHistory
          _ <- eoriStore.saveEoris(eoriHistory)
        } yield result
      }
    )
  )

}
