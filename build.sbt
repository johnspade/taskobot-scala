import Dependencies._

name := "tasko_bot"

scalaVersion := "3.3.0-RC3"

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:privates"
)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= distributionDependencies ++ testDependencies.map(_ % Test)

enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin)

ThisBuild / dynverSeparator := "-"
dockerBaseImage             := "eclipse-temurin:11.0.18_10-jre-alpine"
dockerExposedPorts ++= Seq(8080)
dockerAliases += dockerAlias.value.withTag(Option("latest"))
