import sbt.librarymanagement.syntax._

object Dependencies {
  object V {
    val telegramium      = "7.56.0"
    val tgbotUtils       = "0.6.0"
    val zio              = "2.0.1"
    val zioCats          = "3.3.0+10-274fad37-SNAPSHOT"
    val zioLogging       = "2.1.1"
    val logback          = "1.2.7"
    val cats             = "2.6.1"
    val pureconfig       = "0.17.1"
    val doobie           = "1.0.0-RC1"
    val flyway           = "8.0.5"
    val http4s           = "0.23.6"
    val postgresql       = "42.3.1"
    val testcontainers   = "0.40.6"
    val mockitoScala     = "1.16.46"
    val mockServerClient = "5.13.2"
  }

  val distributionDependencies = Seq(
    "io.github.apimorphism" %% "telegramium-core"    % V.telegramium,
    "io.github.apimorphism" %% "telegramium-high"    % V.telegramium,
    "ru.johnspade"          %% "tgbot-utils"         % V.tgbotUtils,
    "dev.zio"               %% "zio"                 % V.zio,
    "dev.zio"               %% "zio-macros"          % V.zio,
    "dev.zio"               %% "zio-interop-cats"    % V.zioCats,
    "dev.zio"               %% "zio-logging-slf4j"   % V.zioLogging,
    "ch.qos.logback"         % "logback-classic"     % V.logback,
    "org.typelevel"         %% "cats-core"           % V.cats,
    "com.github.pureconfig" %% "pureconfig-core"     % V.pureconfig,
    "org.tpolecat"          %% "doobie-core"         % V.doobie,
    "org.tpolecat"          %% "doobie-hikari"       % V.doobie,
    "org.tpolecat"          %% "doobie-postgres"     % V.doobie,
    "org.flywaydb"           % "flyway-core"         % V.flyway,
    "org.http4s"            %% "http4s-blaze-client" % V.http4s,
    "org.postgresql"         % "postgresql"          % V.postgresql
  )

  val testDependencies = Seq(
    "dev.zio"        %% "zio-test"                               % V.zio,
    "dev.zio"        %% "zio-test-sbt"                           % V.zio,
    "com.dimafeng"   %% "testcontainers-scala-postgresql"        % V.testcontainers,
    "com.dimafeng"   %% "testcontainers-scala-mockserver"        % V.testcontainers,
    "org.mock-server" % "mockserver-client-java-no-dependencies" % V.mockServerClient
  )
}
