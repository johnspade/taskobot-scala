package ru.johnspade.taskobot

import org.flywaydb.core.Flyway
import ru.johnspade.taskobot.Configuration.DbConfig
import zio.blocking._
import zio.{Has, RIO, ZIO}

object FlywayMigration {
  val migrate: RIO[Has[DbConfig] with Blocking, Any] =
    ZIO.accessM { env =>
      val cfg = env.get[DbConfig]
      effectBlocking {
        Flyway
          .configure()
          .dataSource(s"jdbc:postgresql://${cfg.host}:${cfg.port}/${cfg.database}", cfg.user, cfg.password)
          .load()
          .migrate()
      }
        .unit
    }
}
