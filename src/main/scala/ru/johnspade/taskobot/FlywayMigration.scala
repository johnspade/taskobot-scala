package ru.johnspade.taskobot

import zio.*

import org.flywaydb.core.Flyway

object FlywayMigration {
  val migrate: RIO[DbConfig, Unit] =
    ZIO.serviceWithZIO[DbConfig] { cfg =>
      ZIO.attemptBlocking {
        Flyway
          .configure()
          .dataSource(cfg.url, cfg.user, cfg.password)
          .baselineOnMigrate(true)
          .load()
          .migrate()
      }.unit
    }
}
