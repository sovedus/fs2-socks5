lazy val tlScalafixVersion = "0.5.0"
lazy val log4catsVersion = "2.7.1"

ThisBuild / tlBaseVersion := "0.2"
ThisBuild / organization := "io.github.sovedus"
ThisBuild / organizationName := "Sovedus"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("sovedus", "")
)
ThisBuild / tlCiReleaseBranches := Seq("master")
ThisBuild / githubWorkflowTargetBranches := Seq("master")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    commands = List("coverage", "test", "coverageReport"),
    name = Some("Build project")
  )
)
ThisBuild / githubWorkflowPublish += WorkflowStep.Use(
  name = Some("Upload coverage reports to Codecov"),
  ref = UseRef.Public("codecov", "codecov-action", "v5"),
  params = Map("token" -> "${{ secrets.CODECOV_TOKEN }}")
)

ThisBuild / scalafixDependencies ++= Seq(
  "org.typelevel" %% "typelevel-scalafix" % tlScalafixVersion,
  "org.typelevel" %% "typelevel-scalafix-cats" % tlScalafixVersion,
  "org.typelevel" %% "typelevel-scalafix-cats-effect" % tlScalafixVersion,
  "org.typelevel" %% "typelevel-scalafix-fs2" % tlScalafixVersion
)
ThisBuild / semanticdbOptions ++= Seq("-P:semanticdb:synthetics:on")
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalaVersion := "2.13.16"

addCommandAlias("testCoverage", "clean;coverage;test;coverageReport;coverageOff")
addCommandAlias("fmt", "scalafmtAll;scalafmtSbt")

lazy val noPublishSettings = Seq(
  publishArtifact := false,
  packagedArtifacts := Map.empty,
  publish / skip := true,
  publish := {},
  publishLocal := {}
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "fs2-socks5",
    Test / fork := true,
    Test / logBuffered := false,
    Test / javaOptions ++= Seq(
      "-Dcats.effect.report.unhandledFiberErrors=false"
    ),
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % "3.12.2",
      "org.typelevel" %% "log4cats-core" % log4catsVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.18" % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0" % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test,
      "org.scalamock" %% "scalamock" % "7.5.0" % Test,
      "org.scalamock" %% "scalamock-cats-effect" % "7.5.0" % Test
    )
  )

lazy val example = project
  .dependsOn(root)
  .settings(
    Compile / run / fork := true,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion
    )
  )
  .enablePlugins(NoPublishPlugin)
