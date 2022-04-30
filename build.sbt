import Dependencies._

name := "tasko_bot"

scalaVersion := "3.1.2"

scalacOptions ++= Seq(
  "-language:higherKinds"
)

libraryDependencies ++= distributionDependencies ++ testDependencies.map(_ % Test)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin)

ThisBuild / dynverSeparator := "-"
dockerBaseImage             := "adoptopenjdk/openjdk11:jre-11.0.10_9-alpine"
dockerExposedPorts ++= Seq(8080)
