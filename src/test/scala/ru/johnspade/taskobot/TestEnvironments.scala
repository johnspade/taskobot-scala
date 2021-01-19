package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.DbConfig
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.TestContainer.Postgres
import zio.blocking.Blocking
import zio.{Has, URLayer, ZLayer}

object TestEnvironments {
  type PostgresITEnv = Postgres with SessionPool with Has[DbConfig]

  val itLayer: URLayer[Blocking, PostgresITEnv] = {
    val postgres = TestContainer.postgres
    val dbConfig = postgres >>> TestContainer.dbConfig
    val sessionPool = dbConfig >>> SessionPool.live
    (postgres ++ sessionPool ++ dbConfig ++ ZLayer.requires[Blocking]) >>>
      ZLayer.fromFunctionManyM { r =>
        FlywayMigration.migrate.orDie.provide(r).as(r)
      }
  }
}
