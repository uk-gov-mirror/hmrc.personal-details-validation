/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.matching

import javax.inject.{Inject, Singleton}

import cats.data.EitherT
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.{MatchResult, MatchingError}
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetails
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchingConnector @Inject()(httpClient: HttpClient, connectorConfig: MatchingConnectorConfig) {

  import connectorConfig.authenticatorBaseUrl
  import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsFormat._

  def doMatch(personalDetails: PersonalDetails)
             (implicit headerCarrier: HeaderCarrier,
              executionContext: ExecutionContext): EitherT[Future, MatchingError, MatchResult] =
    EitherT(httpClient.POST[JsObject, Either[MatchingError, MatchResult]](
      url = s"$authenticatorBaseUrl/match",
      body = personalDetails.toJson
    ))

  private implicit val matchingResultHttpReads: HttpReads[Either[MatchingError, MatchResult]] = new HttpReads[Either[MatchingError, MatchResult]] {
    override def read(method: String, url: String, response: HttpResponse): Either[MatchingError, MatchResult] = response.status match {
      case OK => Right(MatchSuccessful(response.json.as[PersonalDetails]))
      case UNAUTHORIZED => Right(MatchFailed)
      case other => Left(MatchingError(s"Unexpected response from $method $url with status: '$other' and body: ${response.body}"))
    }
  }

  private implicit class PersonalDetailsSerializer(personalDetails: PersonalDetails) {
    lazy val toJson: JsObject = Json.obj(
      "firstName" -> personalDetails.firstName,
      "lastName" -> personalDetails.lastName,
      "dateOfBirth" -> personalDetails.dateOfBirth,
      "nino" -> personalDetails.nino
    )
  }
}

object MatchingConnector {

  sealed trait MatchResult

  case class MatchingError(message: String)

  object MatchResult {

    case class MatchSuccessful(matchedPerson: PersonalDetails) extends MatchResult
    case object MatchFailed extends MatchResult
  }
}