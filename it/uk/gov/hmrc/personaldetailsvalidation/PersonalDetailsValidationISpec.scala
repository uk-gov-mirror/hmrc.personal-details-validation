package uk.gov.hmrc.personaldetailsvalidation

import java.util.UUID.randomUUID

import play.api.http.ContentTypes.JSON
import play.api.http.Status._
import play.api.libs.json.{JsUndefined, JsValue, Json}
import play.mvc.Http.HeaderNames.{CONTENT_TYPE, LOCATION}
import uk.gov.hmrc.support.BaseIntegrationSpec
import uk.gov.hmrc.support.stubs.AuthenticatorStub
import uk.gov.hmrc.support.wiremock.{WiremockSpecSupport, WiremockedServiceSupport}

class PersonalDetailsValidationISpec extends BaseIntegrationSpec with WiremockedServiceSupport with WiremockSpecSupport {

  override val wiremockedServices: List[String] = List("authenticator")
  override lazy val additionalConfiguration = wiremockedServicesConfiguration

  "POST /personal-details-validation" should {

    "return OK with success validation status when provided personal details can be matched by Authenticator" in new Setup {

      AuthenticatorStub.expecting(personalDetails).respondWithOK()

      val createResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      val validationId = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)
      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
      (getResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetails)
    }

    "return OK with failure validation status when provided personal details cannot be matched by Authenticator" in new Setup {

      AuthenticatorStub.expecting(personalDetails).respondWith(UNAUTHORIZED)

      val createResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      val validationId = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)
      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "failure"
      (getResponse.json \ "personalDetails") mustBe a[JsUndefined]
    }

    "return BAD_GATEWAY when Authenticator returns an unexpected status code" in new Setup {
      AuthenticatorStub.expecting(personalDetails).respondWith(NO_CONTENT)

      val createResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe BAD_GATEWAY
    }

    "return BAD Request if mandatory fields are missing" in new Setup {
      val createResponse = sendCreateValidationResourceRequest("{}").futureValue

      createResponse.status mustBe BAD_REQUEST

      (createResponse.json \ "errors").as[List[String]] must contain only(
        "firstName is missing",
        "lastName is missing",
        "dateOfBirth is missing",
        "nino is missing"
      )
    }
  }

  "GET /personal-details-validation/id" should {

    "return NOT FOUND if id is UUID but invalid id" in {
      val getResponse = wsUrl(s"/personal-details-validation/${randomUUID().toString}").get().futureValue
      getResponse.status mustBe NOT_FOUND
    }

    "return NOT FOUND if id is not a valid UUID" in {
      val getResponse = wsUrl(s"/personal-details-validation/foo-bar").get().futureValue
      getResponse.status mustBe NOT_FOUND
    }
  }

  private trait Setup {

    val personalDetails =
      """
        |{
        |   "firstName": "Jim",
        |   "lastName": "Ferguson",
        |   "nino": "AA000003D",
        |   "dateOfBirth": "1948-04-23"
        |}
      """.stripMargin

    def sendCreateValidationResourceRequest(body: String) =
      wsUrl("/personal-details-validation")
        .withHeaders(CONTENT_TYPE -> JSON)
        .post(body)
  }
}
