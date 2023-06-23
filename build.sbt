import Dependencies._

name := "tasko_bot"

scalaVersion := "3.3.0"

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:privates",
  "-deprecation"
)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= distributionDependencies ++ testDependencies.map(_ % Test)

enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin)

ThisBuild / dynverSeparator := "-"
dockerBaseImage             := "eclipse-temurin:17.0.6_10-jre-jammy"
dockerExposedPorts ++= Seq(8080)
dockerRepository := Some("ghcr.io")
dockerUsername   := Some("johnspade")
dockerLabels     := Map("org.opencontainers.image.source" -> "https://github.com/johnspade/tasko_bot")
