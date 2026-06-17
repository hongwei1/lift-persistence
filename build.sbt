import Dependencies._

ThisBuild / organization       := "net.liftweb"
ThisBuild / version            := "3.5.0-lift-persistence"
ThisBuild / homepage           := Some(url("https://github.com/OpenBankProject/lift-persistence"))
ThisBuild / licenses           += ("Apache License, Version 2.0", url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / startYear          := Some(2006)
ThisBuild / organizationName   := "WorldWide Conferencing, LLC"

val scala212Version = "2.12.20"
val scala213Version = "2.13.16"

ThisBuild / scalaVersion       := scala212Version
ThisBuild / crossScalaVersions := Seq(scala212Version, scala213Version)

ThisBuild / libraryDependencies ++= Seq(specs2, specs2Matchers, specs2Mock, scalacheck, scalactic, scalatest)

ThisBuild / scalacOptions ++= Seq("-deprecation")

ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/hongwei1/lift-persistence"),
  "scm:git:https://github.com/hongwei1/lift-persistence.git"
))

// The library lives in the lift-persistence/ subproject ON PURPOSE.
// JitPack names sbt artifacts by module: a named subproject publishes as
// com.github.<user>.<repo>:lift-persistence_<scalaVersion>, which is the
// coordinate downstream consumers (OBP-API) depend on. A flattened root
// project would instead publish as com.github.<user>:<repo> — losing the
// scala-version suffix and breaking the artifactId. Do not flatten.
lazy val `lift-persistence` =
  Project("lift-persistence", file("lift-persistence"))
    .settings(
      description := "Lift Persistence — OBP fork single-artifact ORM (mapper + db + proto + util + common)",
      Test / parallelExecution := false,
      libraryDependencies ++= Seq(
        scala_reflect(scalaVersion.value),
        slf4j_api,
        logback,
        scala_xml,
        joda_time,
        joda_convert,
        commons_codec,
        xerces,
        jbcrypt,
        // test
        h2,
        derby
      ),
      Test / initialize := {
        System.setProperty(
          "derby.stream.error.file",
          ((Test / crossTarget).value / "derby.log").absolutePath
        )
      }
    )
