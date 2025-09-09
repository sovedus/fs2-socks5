lazy val tlScalafixVersion = "0.5.0"

ThisBuild / tlBaseVersion := "0.1"
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
  .aggregate(core)
  .settings(name := "fs2-socks5")
  .enablePlugins(NoPublishPlugin)

lazy val core = project.settings(
  name := "fs2-socks5",
  libraryDependencies ++= Seq(
    "co.fs2" %%% "fs2-io" % "3.12.2",
    "org.scalameta" %% "munit" % "1.1.1" % Test,
    "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test
  ),
  Test / testOptions += Tests.Argument("+l")
)

lazy val example = project
  .dependsOn(core)
  .settings(
    Compile / run / fork := true
  )
  .enablePlugins(NoPublishPlugin)
