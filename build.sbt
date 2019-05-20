import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import play.core.PlayVersion.{current => currentPlayVersion}

val appName = "customs-data-store"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(
    majorVersion                     := 0,
    libraryDependencies              ++= compileDeps ++ testDeps
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)


val compileDeps = Seq(

  "uk.gov.hmrc"             %% "simple-reactivemongo"     % "7.19.0-play-25",
  "org.sangria-graphql"     %% "sangria"                  % "1.4.2",
  "org.sangria-graphql"     %% "sangria-play-json"        % "1.0.0",
  "uk.gov.hmrc"             %% "bootstrap-play-25"        % "4.11.0"
)

val testDeps = Seq(
  "org.scalatest"           %% "scalatest"                % "3.0.4"                 % "test",
  "com.typesafe.play"       %% "play-test"                % currentPlayVersion      % "test",
  "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test, it",
  "uk.gov.hmrc"             %% "service-integration-test" % "0.2.0"                 % "test, it",
  "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.0"                 % "test, it",
  "org.mockito"             %  "mockito-core"             % "2.13.0"                % "test,it"
)
