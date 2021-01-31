import sbt.librarymanagement.syntax._

object Dependencies {
  object V {
    val telegramium = "3.50.0"
    val tgbotUtils = "0.1.0"
    val zio = "1.0.1"
    val zioCats = "2.1.4.0"
    val cats = "2.2.0"
    val pureconfig = "0.12.3"
    val skunk = "0.0.21"
    val flyway = "7.4.0"
    val enumeratum = "1.6.1"
    val supertagged = "2.0-RC2"
    val kantan = "0.6.0"
    val magnolia = "0.16.0"
    val http4s = "0.21.8"
    val scalingua = "0.9"
    val postgresql = "42.2.18"
    val chimney = "0.6.1"
    val testcontainers = "0.38.8"
    val mockitoScala = "1.16.13"
  }

  val distributionDependencies = Seq(
    "io.github.apimorphism" %% "telegramium-core" % V.telegramium,
    "io.github.apimorphism" %% "telegramium-high" % V.telegramium,
    "ru.johnspade" %% "tgbot-utils" % V.tgbotUtils,
    "dev.zio" %% "zio" % V.zio,
    "dev.zio" %% "zio-macros" % V.zio,
    "dev.zio" %% "zio-interop-cats" % V.zioCats,
    "org.typelevel" %% "cats-core" % V.cats,
    "com.github.pureconfig" %% "pureconfig" % V.pureconfig,
    "com.github.pureconfig" %% "pureconfig-magnolia" % V.pureconfig,
    "org.tpolecat" %% "skunk-core" % V.skunk,
    "org.flywaydb" % "flyway-core" % V.flyway,
    "com.beachape" %% "enumeratum" % V.enumeratum,
    "org.rudogma" %% "supertagged" % V.supertagged,
    "com.nrinaudo" %% "kantan.csv" % V.kantan,
    "com.nrinaudo" %% "kantan.csv-java8" % V.kantan,
    "com.nrinaudo" %% "kantan.csv-enumeratum" % V.kantan,
    "com.propensive" %% "magnolia" % V.magnolia,
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
