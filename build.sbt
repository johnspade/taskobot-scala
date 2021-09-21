import Dependencies._

name := "tasko_bot"

scalaVersion := "2.13.6"

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-Ymacro-annotations"
)

libraryDependencies ++= distributionDependencies ++ testDependencies.map(_ % Test)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

enablePlugins(Scalingua, JavaAppPackaging, DockerPlugin, AshScriptPlugin)

Compile / templateTarget := file("src/main/locales/messages.pot")
Test / compileLocales / sourceDirectories := Seq(file("src/main/locales"))

ThisBuild / dynverSeparator := "-"
dockerBaseImage := "adoptopenjdk/openjdk11:jre-11.0.10_9-alpine"
dockerExposedPorts ++= Seq(8080)
