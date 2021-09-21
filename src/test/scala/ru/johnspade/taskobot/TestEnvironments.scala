package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.DbConfig
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.PostgresContainer.Postgres
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Has, ZLayer}

object TestEnvironments {
  type PostgresITEnv = Postgres with DbTransactor with Has[DbConfig] with Blocking with Clock

  val itLayer: ZLayer[Blocking with Clock, Nothing, PostgresITEnv] = {
    val postgres = PostgresContainer.container
    val dbConfig = postgres >>> PostgresContainer.dbConfig
    val sessionPool = dbConfig ++ ZLayer.requires[Blocking] ++ ZLayer.requires[Clock] >>> DbTransactor.live
    (postgres ++ sessionPool ++ dbConfig ++ ZLayer.requires[Blocking] ++ ZLayer.requires[Clock]) >>>
      ZLayer.requires[PostgresITEnv] ++ FlywayMigration.migrate.orDie.toLayer.passthrough
  }
}
