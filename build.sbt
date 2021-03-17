import Dependencies._

name := "tasko_bot"

scalaVersion := "2.13.5"

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-language:existentials",
  "-Ymacro-annotations"
)

libraryDependencies ++= distributionDependencies ++ testDependencies.map(_ % Test)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full)

enablePlugins(Scalingua, JavaAppPackaging, DockerPlugin, AshScriptPlugin)

templateTarget in Compile := file("src/main/locales/messages.pot")
sourceDirectories in (Test, compileLocales) := Seq(file("src/main/locales"))

ThisBuild / dynverSeparator := "-"
dockerBaseImage := "adoptopenjdk/openjdk11:jre-11.0.10_9-alpine"
dockerExposedPorts ++= Seq(8080)
