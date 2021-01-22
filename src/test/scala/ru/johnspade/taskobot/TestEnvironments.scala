package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.DbConfig
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.PostgresContainer.Postgres
import zio.blocking.Blocking
import zio.{Has, URLayer, ZLayer}

object TestEnvironments {
  type PostgresITEnv = Postgres with SessionPool with Has[DbConfig]

  val itLayer: ZLayer[Blocking, Nothing, PostgresITEnv] = {
    val postgres = PostgresContainer.container
    val dbConfig = postgres >>> PostgresContainer.dbConfig
    val sessionPool = dbConfig >>> SessionPool.live
    (postgres ++ sessionPool ++ dbConfig ++ ZLayer.requires[Blocking]) >>>
      ZLayer.requires[PostgresITEnv] ++ FlywayMigration.migrate.orDie.toLayer.passthrough
  }
}
