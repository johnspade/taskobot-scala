package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.DbConfig
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.PostgresContainer.Postgres
import zio.blocking.Blocking
import zio.{Has, ZLayer}

object TestEnvironments {
  type PostgresITEnv = Postgres with DbTransactor with Has[DbConfig]

  val itLayer: ZLayer[Blocking, Nothing, PostgresITEnv] = {
    val postgres = PostgresContainer.container
    val dbConfig = postgres >>> PostgresContainer.dbConfig
    val sessionPool = dbConfig ++ ZLayer.requires[Blocking] >>> DbTransactor.live
    (postgres ++ sessionPool ++ dbConfig ++ ZLayer.requires[Blocking]) >>>
      ZLayer.requires[PostgresITEnv] ++ FlywayMigration.migrate.orDie.toLayer.passthrough
  }
}
