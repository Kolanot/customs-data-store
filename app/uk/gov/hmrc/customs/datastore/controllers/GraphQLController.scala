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

package uk.gov.hmrc.customs.datastore.controllers

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.customs.datastore.graphql.GraphQL
import play.api.libs.json._
import play.api.mvc._
import sangria.ast.Document
import sangria.execution._
import sangria.marshalling.playJson._
import sangria.parser.QueryParser
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.customs.datastore.services.ServerTokenAuthorization
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


/**
  * Creates an `Action` to handle HTTP requests.
  *
  * @param graphQL          an object containing a graphql schema of the entire application
  * @param cc               base controller components dependencies that most controllers rely on.
  * @param executionContext execute program logic asynchronously, typically but not necessarily on a thread pool
  */
@Singleton
class GraphQLController @Inject()(val serverAuth: ServerTokenAuthorization, graphQL: GraphQL)
                                 (implicit val executionContext: ExecutionContext) extends BaseController {

  /**
    * Parses graphql body of incoming request.
    *
    * @return an 'Action' to handles a request and generates a result to be sent to the client
    */
  def graphqlBody(): Action[JsValue] = serverAuth.async(parse.json) {
    implicit request: Request[JsValue] =>

        val extract: JsValue => (String, Option[String], Option[JsObject]) = query => (
          (query \ "query").as[String],
          (query \ "operationName").asOpt[String],
          (query \ "variables").toOption.flatMap {
            case JsString(vars) => Some(parseVariables(vars))
            case obj: JsObject => Some(obj)
            case _ => None
          }
        )

        val maybeQuery: Try[(String, Option[String], Option[JsObject])] = Try {
          request.body match {
            case arrayBody@JsArray(_) => extract(arrayBody.value(0))
            case objectBody@JsObject(_) => extract(objectBody)
            case otherType =>
              throw new Error {
                s"/graphql endpoint does not support request body of type [${otherType.getClass.getSimpleName}]"
              }
          }
        }

        maybeQuery match {
          case Success((query, operationName, variables)) => executeQuery(query, variables, operationName)
          case Failure(error) => Future.successful {
            BadRequest(error.getMessage)
          }
        }
  }

  /**
    * Analyzes and executes incoming graphql query, and returns execution result.
    *
    * @param query     graphql body of request
    * @param variables incoming variables passed in the request
    * @param operation name of the operation (queries or mutations)
    * @return simple result, which defines the response header and a body ready to send to the client
    */
  def executeQuery(query: String, variables: Option[JsObject] = None, operation: Option[String] = None): Future[Result] = {
    Logger.logger.info(s"query: $query")
    QueryParser.parse(query) match {
      case Success(queryAst: Document) =>
        Executor.execute(
        schema = graphQL.schema,
        queryAst = queryAst,
        variables = variables.getOrElse(Json.obj())
      ).map(Ok(_))
        .recover {
          case error: QueryAnalysisError => Logger.logger.error(s"graphql error: ${error.getMessage}"); BadRequest(error.resolveError)
          case error: ErrorWithResolver => Logger.logger.error(s"graphql error: ${error.getMessage}"); InternalServerError(error.resolveError)
        }
      case Failure(ex) => Logger.logger.error(s"graphql error: ${ex.getMessage}"); Future(BadRequest(s"${ex.getMessage}"))
    }
  }

  /**
    * Parses variables of incoming query.
    *
    * @param variables variables from incoming query
    * @return JsObject with variables
    */
  def parseVariables(variables: String): JsObject = if (variables.trim.isEmpty || variables.trim == "null") Json.obj()
  else Json.parse(variables).as[JsObject]
}

