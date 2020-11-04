import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import play.core.PlayVersion.{current => currentPlayVersion}

val appName = "customs-data-store"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(
    majorVersion                     := 0,
    libraryDependencies              ++= compileDeps ++ testDeps,
    dependencyOverrides ++= overrides
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(parallelExecution in Test := false)


val compileDeps = Seq(

  "uk.gov.hmrc"             %% "simple-reactivemongo"     % "7.30.0-play-26",
  "org.sangria-graphql"     %% "sangria"                  % "1.4.2",
  "org.sangria-graphql"     %% "sangria-play-json"        % "1.0.3",
  "uk.gov.hmrc"             %% "bootstrap-play-26"        % "1.11.0"
)

val testDeps = Seq(
  "org.scalatest"           %% "scalatest"                % "3.0.8"                 % "test",
  "com.typesafe.play"       %% "play-test"                % currentPlayVersion      % "test",
  "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test, it",
  "org.scalatestplus.play"  %% "scalatestplus-play"       % "3.1.0"                 % "test, it",
  "org.mockito"             %  "mockito-all"              % "1.10.19"                % "test,it"
)

val akkaVersion = "2.5.23"
val akkaHttpVersion = "10.0.15"

val overrides = Set(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
)
