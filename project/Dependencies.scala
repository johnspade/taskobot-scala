import sbt.librarymanagement.syntax._

object Dependencies {
  object V {
    val telegramium = "5.53.0"
    val tgbotUtils = "0.4.0"
    val zio = "1.0.5"
    val zioCats = "2.3.1.0"
    val zioLogging = "0.5.8"
    val logback = "1.2.3"
    val cats = "2.4.2"
    val pureconfig = "0.14.1"
    val doobie = "0.12.1"
    val flyway = "7.7.0"
    val enumeratum = "1.6.1"
    val supertagged = "2.0-RC2"
    val kantan = "0.6.1"
    val http4s = "0.21.20"
    val scalingua = "1.0"
    val postgresql = "42.2.19"
    val chimney = "0.6.1"
    val testcontainers = "0.39.3"
    val mockitoScala = "1.16.32"
  }

  val distributionDependencies = Seq(
    "io.github.apimorphism" %% "telegramium-core" % V.telegramium,
    "io.github.apimorphism" %% "telegramium-high" % V.telegramium,
    "ru.johnspade" %% "tgbot-utils" % V.tgbotUtils,
    "dev.zio" %% "zio" % V.zio,
    "dev.zio" %% "zio-macros" % V.zio,
    "dev.zio" %% "zio-interop-cats" % V.zioCats,
    "dev.zio" %% "zio-logging-slf4j" % V.zioLogging,
    "ch.qos.logback" % "logback-classic" % V.logback,
    "org.typelevel" %% "cats-core" % V.cats,
    "com.github.pureconfig" %% "pureconfig" % V.pureconfig,
    "com.github.pureconfig" %% "pureconfig-magnolia" % V.pureconfig,
    "org.tpolecat" %% "doobie-core" % V.doobie,
    "org.tpolecat" %% "doobie-hikari" % V.doobie,
    "org.tpolecat" %% "doobie-postgres" % V.doobie,
    "org.flywaydb" % "flyway-core" % V.flyway,
    "com.beachape" %% "enumeratum" % V.enumeratum,
    "org.rudogma" %% "supertagged" % V.supertagged,
    "com.nrinaudo" %% "kantan.csv" % V.kantan,
    "com.nrinaudo" %% "kantan.csv-java8" % V.kantan,
    "com.nrinaudo" %% "kantan.csv-enumeratum" % V.kantan,
    "org.http4s" %% "http4s-blaze-client" % V.http4s,
    "ru.makkarpov" %% "scalingua" % V.scalingua,
    "org.postgresql" % "postgresql" % V.postgresql
  )

  val testDependencies = Seq(
    "dev.zio" %% "zio-test" % V.zio,
    "dev.zio" %% "zio-test-sbt" % V.zio,
    "io.scalaland" %% "chimney" % V.chimney,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % V.testcontainers,
    "org.mockito" %% "mockito-scala" % V.mockitoScala
  )
}
