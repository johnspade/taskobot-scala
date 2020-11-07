import sbt.librarymanagement.syntax._

object Dependencies {
  object V {
    val zio = "1.0.1"
    val zioCats = "2.1.4.0"
    val pureconfig = "0.12.3"
    val skunk = "0.0.21"
    val flyway = "6.4.1"
    val enumeratum = "1.6.1"
    val supertagged = "2.0-RC2"
    val kantan = "0.6.0"
    val magnolia = "0.16.0"
    val http4s = "0.21.8"
    val scalingua = "0.9"
    val postgresql = "42.2.18"
  }

  val distributionDependencies = Seq(
    "dev.zio" %% "zio" % V.zio,
    "dev.zio" %% "zio-macros" % V.zio,
    "dev.zio" %% "zio-interop-cats" % V.zioCats,
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
    "dev.zio" %% "zio-test-sbt" % V.zio
  )
}
