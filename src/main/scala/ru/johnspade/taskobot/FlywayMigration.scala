package ru.johnspade.taskobot

import org.flywaydb.core.Flyway
import ru.johnspade.taskobot.Configuration.DbConfig
import zio.blocking._
import zio.{Has, RIO, ZIO}

object FlywayMigration {
  val migrate: RIO[Has[DbConfig] with Blocking, Unit] =
    ZIO.accessM { env =>
      val config = env.get[DbConfig]
      effectBlocking {
        Flyway
          .configure()
          .dataSource(config.url, config.user, config.password)
          .load()
          .migrate()
      }
        .unit
    }
}
