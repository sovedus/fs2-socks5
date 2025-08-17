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

val Scala213 = "2.13.16"
val Scala3 = "3.3.6"
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / scalaVersion := Scala213

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
  .aggregate(core.jvm, core.native)
  .settings(name := "fs2-socks5")
  .enablePlugins(NoPublishPlugin)

lazy val core = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .settings(
    name := "fs2-socks5",
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % "3.12.0",
      "org.scalameta" %%% "munit" % "1.0.0" % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.1.0" % Test
    ),
    Test / testOptions += Tests.Argument("+l")
  )

lazy val example = project
  .dependsOn(core.jvm, core.native)
  .settings(
    Compile / run / fork := true
  )
  .enablePlugins(NoPublishPlugin)
