import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test()


  private val compile = Seq(
    "org.typelevel" %% "cats-core" % "2.0.0",
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "4.2.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.31.0-play-27",
    "uk.gov.hmrc" %% "domain" % "5.11.0-play-27",
    ws
  )

  private def test(scope: String = "test,it") = Seq(
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
    "org.scalamock" %% "scalamock" % "4.1.0" % "test",
    "com.itv" %% "scalapact-circe-0-9" % "2.3.16" % "test, it",
    "com.itv" %% "scalapact-http4s-0-18" % "2.3.16" % "test, it",
    "com.itv" %% "scalapact-scalatest" % "2.3.16" % "test, it",
    "org.scalatest" %% "scalatest" % "3.0.5" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "it",
    "uk.gov.hmrc" %% "service-integration-test" % "1.0.0-play-27" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "5.0.0-play-27" % "test",
    "com.github.tomakehurst" % "wiremock-jre8" % "2.27.2" % "it"
  )
}
