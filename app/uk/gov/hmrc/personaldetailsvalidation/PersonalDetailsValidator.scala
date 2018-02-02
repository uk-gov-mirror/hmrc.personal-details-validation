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

package uk.gov.hmrc.personaldetailsvalidation

import javax.inject.{Inject, Singleton}

import cats.data.EitherT
import cats.implicits._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.MatchingEventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.{MatchResult, MatchingError}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, Future}

@Singleton
private class PersonalDetailsValidator @Inject()(private val matchingConnector: MatchingConnector,
                                                 private val personalDetailsValidationRepository: PersonalDetailsValidationRepository,
                                                 private val matchingEventsSender: MatchingEventsSender)
                                                (implicit private val uuidProvider: UUIDProvider) {

  def validate(personalDetails: PersonalDetails)
              (implicit headerCarrier: HeaderCarrier,
               executionContext: ExecutionContext): EitherT[Future, MatchingError, ValidationId] =
    for {
      matchResult <- getMatchingResult(personalDetails)
      _ <- sendMatchingResultEvent(matchResult)
      personalDetailsValidation = matchResult.toPersonalDetailsValidation(optionallyHaving = personalDetails)
      _ <- persist(personalDetailsValidation)
    } yield personalDetailsValidation.id

  private def getMatchingResult(personalDetails: PersonalDetails)(implicit headerCarrier: HeaderCarrier,
                                                                  executionContext: ExecutionContext) = {
    for {
      error <- matchingConnector.doMatch(personalDetails).swap
      _ <- sendMatchingErrorEvent
    } yield error
  }.swap

  private def sendMatchingResultEvent(matchResult: MatchResult)(implicit headerCarrier: HeaderCarrier,
                                                                executionContext: ExecutionContext) =
    EitherT.right[MatchingError](matchingEventsSender.sendMatchResultEvent(matchResult))

  private def sendMatchingErrorEvent(implicit headerCarrier: HeaderCarrier,
                                     executionContext: ExecutionContext) = EitherT.right[MatchResult](matchingEventsSender.sendMatchingErrorEvent)

  private def persist(personalDetailsValidation: PersonalDetailsValidation)(implicit headerCarrier: HeaderCarrier,
                                                                            executionContext: ExecutionContext) =
    EitherT.right[MatchingError](personalDetailsValidationRepository.create(personalDetailsValidation))

  private implicit class MatchResultOps(matchResult: MatchResult) {
    def toPersonalDetailsValidation(optionallyHaving: PersonalDetails): PersonalDetailsValidation = matchResult match {
      case MatchSuccessful => PersonalDetailsValidation.successful(optionallyHaving)
      case MatchFailed => PersonalDetailsValidation.failed()
    }
  }

}
