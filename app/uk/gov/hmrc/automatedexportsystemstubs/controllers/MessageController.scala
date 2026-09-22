/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.automatedexportsystemstubs.controllers

import play.api.{Logger, Logging}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.automatedexportsystemstubs.controllers.actions.ValidatedRequestAction
import uk.gov.hmrc.automatedexportsystemstubs.errors.IE906.Ie906Engine
import uk.gov.hmrc.automatedexportsystemstubs.errors.IE917.IE917Engine
import uk.gov.hmrc.automatedexportsystemstubs.errors.SyncErrorPolicy
import uk.gov.hmrc.automatedexportsystemstubs.models.AckNotification
import uk.gov.hmrc.automatedexportsystemstubs.services.NotificationService
import uk.gov.hmrc.automatedexportsystemstubs.utils.{NotificationXmlBuilder, SyncErrorResponseHelper}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

@Singleton()
class MessageController @Inject() (
  cc:                  ControllerComponents,
  notificationService: NotificationService,
  validatedAction:     ValidatedRequestAction
)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with Logging:

  override val logger = Logger(this.getClass)

  def message(): Action[AnyContent] = validatedAction.async { implicit request =>
    val correlationId = request.headers.get("x-correlation-id").getOrElse("")
    implicit val hc: HeaderCarrier =
      HeaderCarrier(
        extraHeaders = Seq("x-correlation-id" -> correlationId)
      )

    def getXmlErrors(notification: AckNotification, matches: Seq[IE917Engine.MatchResult]) =
      val xmlErrors: List[Elem] = matches.map(IE917Engine.toXmlError).toList
      notificationService
        .sendIE917Notification(notification = notification, correlationId = correlationId, errors = xmlErrors)
        .map(_ => validatedAction.successResponse(request))
        .recover { case e =>
          logger.warn("Failed to send EI917 error notification", e)
          validatedAction.errorResponse(InternalServerError, request)
        }

    def getFunctionalErrors(notification: AckNotification, matches: Seq[Ie906Engine.MatchResult]) =
      val functionalErrors: List[Elem] = matches.map(Ie906Engine.toFunctionalError).toList
      notificationService
        .sendIE906Notification(notification = notification, correlationId = correlationId, errors = functionalErrors)
        .map(_ => validatedAction.successResponse(request))
        .recover { case e =>
          logger.warn("Failed to send IE906 error notification", e)
          validatedAction.errorResponse(InternalServerError, request)
        }

    request.body.asXml.flatMap(_.headOption.collect { case e: Elem => e }) match
      case None =>
        logger.warn(s"Error: Invalid XML: ${request.body.toString}")
        val response =
          SyncErrorResponseHelper.createErrorResponse(
            status = BAD_REQUEST,
            correlationId = correlationId,
            errorMessage = "Expected XML body",
            detail = s"Payload submitted ${request.body.toString}"
          )
        Future.successful(
          validatedAction
            .errorResponse(BadRequest(response.toString), request)
        )

      case Some(elem) =>
        SyncErrorPolicy.syncErrorFor(elem) match
          case Some(syncErr) =>
            logger.warn(
              s"Sync error - status: ${syncErr.status}," +
                s"correlationId: $correlationId," +
                s"message: ${syncErr.message}," +
                s"detail: ${syncErr.detail}"
            )
            Future.successful(
              validatedAction
                .errorResponse(
                  SyncErrorResponseHelper.createErrorResponse(
                    status = syncErr.status,
                    correlationId = correlationId,
                    errorMessage = syncErr.message,
                    detail = syncErr.detail
                  ),
                  request
                )
            )

          case None =>
            val notification = NotificationXmlBuilder.parseIncomingAckXml(correlationId, elem)
            IE917Engine.allMatches(elem) match
              case matches if matches.nonEmpty =>
                getXmlErrors(notification, matches)
              case _ =>
                Ie906Engine.allMatches(elem) match
                  case matches if matches.nonEmpty =>
                    getFunctionalErrors(notification, matches)
                  case _ =>
                    notificationService
                      .sendAckNotification(notification, correlationId)
                      .map(_ => validatedAction.successResponse(request))
                      .recover { case e =>
                        logger.error("Failed to send ACK notification", e)
                        validatedAction.errorResponse(InternalServerError, request)
                      }
  }
