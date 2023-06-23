import sbt.librarymanagement.syntax._

object Dependencies {
  object V {
    val telegramium      = "8.67.1"
    val tgbotUtils       = "0.7.1"
    val zio              = "2.0.15"
    val zioCats          = "23.0.0.7"
    val zioLogging       = "2.1.13"
    val zioJson          = "0.5.0"
    val logback          = "1.4.8"
    val cats             = "2.9.0"
    val pureconfig       = "0.17.4"
    val doobie           = "1.0.0-RC4"
    val flyway           = "9.20.0"
    val http4s           = "0.23.15"
    val postgresql       = "42.6.0"
    val testcontainers   = "0.40.17"
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
