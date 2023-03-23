import sbt.librarymanagement.syntax._

object Dependencies {
  object V {
    val telegramium      = "7.66.0"
    val tgbotUtils       = "0.7.0"
    val zio              = "2.0.9"
    val zioCats          = "23.0.0.2"
    val zioLogging       = "2.1.11"
    val zioJson          = "0.4.2"
    val logback          = "1.4.5"
    val cats             = "2.6.1"
    val pureconfig       = "0.17.1"
    val doobie           = "1.0.0-RC1"
    val flyway           = "8.0.5"
    val http4s           = "0.23.6"
    val postgresql       = "42.3.1"
    val testcontainers   = "0.40.14"
    val mockitoScala     = "1.16.46"
    val mockServerClient = "5.15.0"
  }

  val distributionDependencies = Seq(
    "io.github.apimorphism" %% "telegramium-core"    % V.telegramium,
    "io.github.apimorphism" %% "telegramium-high"    % V.telegramium,
    "ru.johnspade"          %% "tgbot-utils"         % V.tgbotUtils,
    "dev.zio"               %% "zio"                 % V.zio,
    "dev.zio"               %% "zio-interop-cats"    % V.zioCats,
    "dev.zio"               %% "zio-logging-slf4j"   % V.zioLogging,
    "dev.zio"               %% "zio-json"            % V.zioJson,
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
