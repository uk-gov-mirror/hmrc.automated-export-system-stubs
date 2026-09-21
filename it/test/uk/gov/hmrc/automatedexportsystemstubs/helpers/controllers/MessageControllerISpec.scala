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

package uk.gov.hmrc.automatedexportsystemstubs.helpers.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{verify as wmVerify, *}
import play.api.test.FakeRequest
import uk.gov.hmrc.automatedexportsystemstubs.helpers.BaseISpec

import scala.xml.Elem
class MessageControllerISpec extends BaseISpec:

  private val endpoint     = "/cds/aesIE507Request/v1"
  private val validHeaders = Seq(
    "Authorization"    -> "auth-token",
    "x-correlation-id" -> "corr-2",
    "accept"           -> "application/xml",
    "content-type"     -> "application/xml",
    "date"             -> "Fri, 31 Jul 2026 10:30:00 GMT",
    "x-message-type"   -> "aesIE507Request",
    "x-forwarded-host" -> "some-host"
  )

  "POST /cds/aesIE507Request/v1" - {

    "route exists" in {
      route(app, FakeRequest(POST, endpoint)).isDefined shouldBe true
    }

    "returns 400 when no XML body" in {
      val request = FakeRequest(POST, endpoint)
        .withHeaders(validHeaders*)

      val result = route(app, request).value
      status(result) shouldBe BAD_REQUEST
    }

    "returns sync 401 when MRN ends with A0" in {
      val body =
        """<AESDigitalNotification>
          |  <Header><messageSender>GB123</messageSender></Header>
          |  <Body><MRN>26GB123456789ABCDE0A0</MRN></Body>
          |</AESDigitalNotification>""".stripMargin

      val request = FakeRequest(POST, endpoint)
        .withHeaders(validHeaders*)
        .withTextBody(body)

      val result = route(app, request).value
      status(result)                                    shouldBe UNAUTHORIZED
      contentType(result)                               shouldBe Some("application/xml")
      (contentAsString(result) contains "UNAUTHORIZED") shouldBe true

    }

    "returns sync 404 when MRN ends with A1" in {
      val body =
        """<AESDigitalNotification>
          |  <Header><messageSender>GB123</messageSender></Header>
          |  <Body><MRN>26GB123456789ABCDE0A1</MRN></Body>
          |</AESDigitalNotification>""".stripMargin

      val result = route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withTextBody(body)
      ).value

      status(result) shouldBe NOT_FOUND
    }

    "returns 204 for async IE906 path (e.g. MRN ends B0 -> code 90 matched and forwarded)" in {
      val body: Elem =
        <AESDigitalNotification>
          <Header>
            <messageSender>GB123</messageSender>
          </Header>
          <Body>
            <ExportOperation>
              <MRN>26GB123456789ABCDEB0</MRN>
              </ExportOperation>
          </Body>
        </AESDigitalNotification>

      stubFor(
        post(urlEqualTo("/automated-export-system-notifications/notification"))
          .willReturn(aResponse().withStatus(204))
      )

      route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withXmlBody(body)
      ).value
      val result = route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withXmlBody(body)
      ).value

      status(result) shouldBe NO_CONTENT
      eventually {
        wmVerify(
          moreThanOrExactly(1),
          postRequestedFor(urlEqualTo("/automated-export-system-notifications/notification"))
            .withHeader("x-correlation-id", equalTo("corr-2"))
            .withHeader("Content-Type", containing("application/xml"))
            .withRequestBody(containing("<messageType>CD906C</messageType>"))
            .withRequestBody(containing("<FunctionalError>"))
        )
      }
    }

    "returns 204 for async IE917 path (e.g. office of exit reference number ends 000 -> code 12 matched and forwarded)" in {
      val body: Elem =
        <AESDigitalNotification>
          <Header>
            <messageSender>GB123</messageSender>
          </Header>
          <Body>
            <CustomsOfficeOExitActual>
              <referenceNumber>some-reference-000</referenceNumber>
            </CustomsOfficeOExitActual>
            <ExportOperation>
              <MRN>26GB123456789ABCDE00</MRN>
            </ExportOperation>
          </Body>
        </AESDigitalNotification>

      stubFor(
        post(urlEqualTo("/automated-export-system-notifications/notification"))
          .willReturn(aResponse().withStatus(204))
      )

      route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withXmlBody(body)
      ).value
      val result = route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withXmlBody(body)
      ).value

      status(result) shouldBe NO_CONTENT
      eventually {
        wmVerify(
          moreThanOrExactly(1),
          postRequestedFor(urlEqualTo("/automated-export-system-notifications/notification"))
            .withHeader("x-correlation-id", equalTo("corr-2"))
            .withHeader("Content-Type", containing("application/xml"))
            .withRequestBody(containing("<messageType>CD917C</messageType>"))
            .withRequestBody(containing("<XmlError>"))
            .withRequestBody(containing("12"))
        )
      }
    }

    "returns 204 for normal ACK path when no sync/IE906 rule matches" in {
      val body: Elem =
        <AESDigitalNotification>
          <Header>
            <messageSender>GB123</messageSender>
          </Header>
          <Body>
            <ExportOperation>
              <MRN>26GB123456789ABCDE00</MRN>
            </ExportOperation>
          </Body>
        </AESDigitalNotification>

      stubFor(
        post(urlEqualTo("/automated-export-system-notifications/notification"))
          .willReturn(aResponse().withStatus(204))
      )

      route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withXmlBody(body)
      ).value
      val result = route(
        app,
        FakeRequest(POST, endpoint)
          .withHeaders(validHeaders*)
          .withXmlBody(body)
      ).value

      status(result) shouldBe NO_CONTENT
      eventually {
        wmVerify(
          moreThanOrExactly(1),
          postRequestedFor(urlEqualTo("/automated-export-system-notifications/notification"))
            .withHeader("x-correlation-id", equalTo("corr-2"))
            .withHeader("Content-Type", containing("application/xml"))
            .withRequestBody(containing("<messageType>ACK</messageType>"))
        )
      }
    }
  }
