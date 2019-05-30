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

import sangria.macros.derive._
import sangria.schema._
import uk.gov.hmrc.customs.datastore.domain.{Email, EmailAddress, EoriPeriod, TraderData}
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.datastore.services.EoriStore
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import sangria.marshalling.FromInput

import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput, ResultMarshaller}

trait InputUnmarshallerGenerator {

  def inputUnmarshaller[T](inputToClassFunc: Map[String, Any] => T): FromInput[T] = new FromInput[T] {
    val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

    override def fromResult(node: marshaller.Node): T = inputToClassFunc(node.asInstanceOf[Map[String, Any]])
  }
}
@Singleton
class TraderDataSchema @Inject()(eoriStore: EoriStore) extends InputUnmarshallerGenerator{

  val NotificationEmail = "notificationEmail"
  val InternalId = "internalId"
  val Address = "address"
  val IsValidated = "isValidated"

  implicit val EoriHistoryType: ObjectType[Unit, EoriPeriod] = deriveObjectType[Unit, EoriPeriod](ObjectTypeName("EoriHistory"))
  implicit val EmailType: ObjectType[Unit, Email] = deriveObjectType[Unit, Email](ObjectTypeName("Email"))
  implicit val TraderDataType: ObjectType[Unit, TraderData] = deriveObjectType[Unit, TraderData](ObjectTypeName("TraderData"))

  implicit val InputEmailType:InputObjectType[InputEmail] = deriveInputObjectType[InputEmail](InputObjectTypeName("email"))
  implicit val InputEmailUnmarshaller: FromInput[InputEmail] = inputUnmarshaller{
    input => InputEmail(
      address = input.get(Address).flatMap(_.asInstanceOf[Option[EmailAddress]]),
      isValidated = input.get(IsValidated).flatMap(_.asInstanceOf[Option[Boolean]])
    )
  }


  val Queries: List[Field[Unit, Unit]] = List(
    Field(
      name = "trader",
      fieldType = ListType(TraderDataType),
      resolve = _ => eoriStore.getTraderData("1234").map(_.toList)
    ),
    Field(
      name = "findEmail",
      fieldType = OptionType(TraderDataType),
      arguments = List(
        Argument("eori", StringType)
      ),
      resolve = sangriaContext => eoriStore.getTraderData(sangriaContext.args.arg[String]("eori"))
    )
  )

  /**
    * List of mutations to work with the entity of TraderData.
    */
  val Mutations: List[Field[Unit, Unit]] = List(
    //Example: {"query" : "mutation {addTrader(credentialId:\"1111111\" eori:\"GB12345678\" notificationEmail:\"abc@goodmail.com\" isValidated:true )}" }
    Field(
      name = "addTrader",
      fieldType = BooleanType,
      arguments = List(
        Argument("credentialId", StringType),
        Argument("eori", StringType),
        Argument("notificationEmail", StringType),
        Argument("isValidated", BooleanType)
      ),
      resolve = sangriaContext =>
        eoriStore.rosmInsert(
          sangriaContext.args.arg[String]("credentialId"),
          sangriaContext.args.arg[String]("eori"),
          sangriaContext.args.arg[String]("notificationEmail"),
          sangriaContext.args.arg[Boolean]("isValidated")
        )
    ),
    Field(
      name = "byInternalId",
      fieldType = BooleanType,
      arguments = List(
        Argument(InternalId, StringType),
        Argument("eori", OptionInputType(StringType)),
        Argument(NotificationEmail, OptionInputType(InputEmailType))
      ),
      resolve = ctx => {
        val internalId = ctx.args.arg[String](InternalId)
        val email = ctx.args.raw.get(NotificationEmail).flatMap(_.asInstanceOf[Option[InputEmail]])
        eoriStore.upsertByInternalId(internalId,email)
      }
    )
  )


}
