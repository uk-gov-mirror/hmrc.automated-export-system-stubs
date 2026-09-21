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

package uk.gov.hmrc.automatedexportsystemstubs.helpers

import play.api.mvc.Headers
import play.api.test.FakeRequest

object TestData:
  val validAuthHeaders: Headers = Headers(
    "x-forwarded-host" -> "10.12.0.4",
    "x-correlation-id" -> "some-correlation-id",
    "date"             -> "Sat, 01 Jun 2024 12:00:00 GMT",
    "content-type"     -> "application/xml",
    "accept"           -> "application/xml",
    "authorization"    -> "Bearer test-token",
    "message-type"     -> "aesIE507Request"
  )

  def requestWithMrn(mrn: String) =
    FakeRequest("POST", "/cds/aesIE507Request/v1")
      .withHeaders(validAuthHeaders)
      .withXmlBody(
        <message>
          <MRN>
            {mrn}
          </MRN>
        </message>
      )
