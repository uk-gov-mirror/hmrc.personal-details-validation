/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.ZoneOffset.UTC
import javax.inject.{Inject, Singleton}

import akka.Done
import com.google.inject.ImplementedBy
import play.api.libs.json.{JsNumber, JsObject}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.datetime.CurrentTimeProvider
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.mongoEntity
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat._
import uk.gov.hmrc.personaldetailsvalidation.formats.TinyTypesFormats._
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, ValidationId}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PersonalDetailsValidationMongoRepository])
private trait PersonalDetailsValidationRepository {

  def create(personalDetails: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): Future[Done]

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]]
}

@Singleton
private class PersonalDetailsValidationMongoRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig, currentTimeProvider: CurrentTimeProvider)(private val mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[PersonalDetailsValidation, ValidationId](
    collectionName = "personal-details-validation",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = mongoEntity(personalDetailsValidationFormats),
    idFormat = personalDetailsValidationIdFormats
  ) with PersonalDetailsValidationRepository {


  override def indexes: Seq[Index] = Seq(
    Index(Seq("createdAt" -> Descending), name = Some("personal-details-validation-ttl-index"), options = BSONDocument("expireAfterSeconds" -> config.collectionTtl.getSeconds))
  )

  def create(personalDetailsValidation: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): Future[Done] = {

    import ImplicitBSONHandlers._

    val writeResult = mongoEntity(personalDetailsValidationFormats).writes(personalDetailsValidation) match {
      case d@JsObject(_) => collection.insert(d ++ JsObject(Seq("createdAt" -> JsNumber(currentTimeProvider().atZone(UTC).toInstant.toEpochMilli))))
      case _ =>
        Future.failed[WriteResult](new Exception("cannot write object"))
    }

    writeResult.map(_ => Done)
  }

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]] =
    findById(personalDetailsValidationId)
}
