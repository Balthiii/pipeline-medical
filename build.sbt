ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / organization := "medical"

lazy val root = (project in file("."))
  .settings(
    name := "pipeline-medical",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )
