package ru.johnspade.taskobot

import zio.*
import zio.interop.catz.*

import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor

object DbTransactor:
  type DbTransactor = Transactor[Task]

  val live =
    ZLayer.scoped {
      def transactor(config: DbConfig)(using rts: Runtime[Any]) = {
        val hikariConfig = new HikariConfig()
        hikariConfig.setDriverClassName(config.driver)
        hikariConfig.setJdbcUrl(config.url)
        hikariConfig.setUsername(config.user)
        hikariConfig.setPassword(config.password)

        HikariTransactor.fromHikariConfig[Task](hikariConfig)
      }

      (ZIO
        .runtime[Any]
        .flatMap { implicit rts =>
          for
            dbConfig   <- ZIO.service[DbConfig]
            transactor <- transactor(dbConfig).toScopedZIO
          yield transactor
        })
        .orDie
    }
