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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json
import play.api.libs.json._
import play.api.mvc._
import play.api.{Logger, LoggerLike}
import sangria.ast.Document
import sangria.execution.{HandledException, _}
import sangria.marshalling.MarshallingUtil._
import sangria.marshalling.playJson._
import sangria.parser.QueryParser
import uk.gov.hmrc.customs.datastore.graphql.GraphQL
import uk.gov.hmrc.customs.datastore.services.ServerTokenAuthorization
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


/**
  * Creates an `Action` to handle HTTP requests.
  *
  * @param serverAuth       ActionBuilder for handling auth on incoming requests
  * @param graphQL          an object containing a graphql schema of the entire application
  * @param executionContext execute program logic asynchronously, typically but not necessarily on a thread pool
  */
@Singleton
class GraphQLController @Inject()(val serverAuth: ServerTokenAuthorization, graphQL: GraphQL, cc: ControllerComponents)
                                 (implicit val executionContext: ExecutionContext) extends BackendController(cc) {

  val log: LoggerLike = Logger(this.getClass)
  
  /**
    * Handles an incoming GraphQL query, and returns any result.
    *
    * @return an 'Action' to handle a request and generate a result to be sent to the client
    */
  def handleRequest(): Action[JsValue] = serverAuth.async(parse.json) {
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



      // TODO consider creating a GraphQLQuery type
      val maybeQuery: Try[(String, Option[String], Option[JsObject])] = Try {
        request.body match {
          case arrayBody@JsArray(_) => extract(arrayBody.value.head)
          case objectBody@JsObject(_) => extract(objectBody)
          case otherType =>
            throw new Error {
              s"/graphQL endpoint does not support request body of type [${otherType.getClass.getSimpleName}]"
            }
        }
      }

      maybeQuery match {
        case Success((query, operationName, variables)) =>
          handleGraphQLQuery(query, variables, operationName)
        case Failure(error) =>
          Future.successful {
            log.error(s"graphQL query parsing error: ${error.getMessage}")
            BadRequest(json.JsObject(Map(
              "ErrorMessage" ->  JsString(error.getMessage),
              "Stack" -> JsString(error.getStackTrace.mkString(" "))))
            )
          }
      }
  }

  /**
    * Analyzes and executes incoming graphql query, and returns execution result.
    *
    * @param query     an unvalidated GraphQL query string
    * @param variables any GraphQL query variables passed in the request
    * @param operation name of the GraphQL operation (queries or mutations)
    * @return simple result, which defines the response header and a body ready to send to the client
    */
  def handleGraphQLQuery(query: String, variables: Option[JsObject] = None, operation: Option[String] = None)
                        (implicit hc: HeaderCarrier): Future[Result] = {
    QueryParser.parse(query) match {
      case Success(queryAst: Document) =>
        executeGraphQLQuery(variables, queryAst)
      case Failure(error) =>
        log.error(s"graphql query parse error: ${error.getMessage}")
        Future(BadRequest(s"${error.getMessage}"))
    }
  }

  private def executeGraphQLQuery(variables: Option[JsObject], queryAst: Document)(implicit hc: HeaderCarrier) = {
    val UPSTREAM_ERROR = "UpstreamError"

    val exceptionHandler = ExceptionHandler {
      case (m, e: Upstream5xxResponse) =>
        HandledException(s"service unavailable: ${e.getMessage}", Map("exception" → m.fromString(UPSTREAM_ERROR)))
      case (m, e: GatewayTimeoutException) => {
        HandledException(s"Gateway timeout: ${e.getMessage}", Map("exception" → m.fromString(UPSTREAM_ERROR)))
      }
    }

    Executor.execute(
      schema = graphQL.schema,
      queryAst = queryAst,
      variables = variables.getOrElse(Json.obj()),
      exceptionHandler = exceptionHandler
    ).map { result =>
      val maybeHandledExceptionMessage = (result \\ "exception").headOption
      maybeHandledExceptionMessage match {
        case Some(JsString(UPSTREAM_ERROR)) =>
          log.error("graphql execute query failed due to an upstream error");
          ServiceUnavailable("Upstream service is unavailable")
        case _ => Ok(result)
      }
    }.recover {
        case error: QueryAnalysisError => log.error(s"graphql error: ${error.getMessage}"); BadRequest(error.resolveError)
        case error: ErrorWithResolver =>  log.error(s"graphql error: ${error.getMessage}"); InternalServerError(error.resolveError)
      }
  }

  /**
    * Parses variables of incoming query.
    *
    * @param variables variables from incoming query
    * @return JsObject with variables
    */
  def parseVariables(variables: String): JsObject =
    if (variables.trim.isEmpty || variables.trim == "null") Json.obj() else Json.parse(variables).as[JsObject]
}

