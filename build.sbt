import Dependencies._

name := "tasko_bot"

version := "0.1"

scalaVersion := "2.13.3"

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-language:existentials",
  "-Ymacro-annotations"
)

libraryDependencies ++= distributionDependencies ++ testDependencies.map(_ % Test)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

lazy val telegramiumCore = ProjectRef(uri("https://github.com/apimorphism/telegramium.git#master"), "telegramium-core")
lazy val telegramiumHigh = ProjectRef(uri("https://github.com/apimorphism/telegramium.git#master"), "telegramium-high")

lazy val root: Project = (project in file("."))
  .dependsOn(telegramiumCore, telegramiumHigh)
