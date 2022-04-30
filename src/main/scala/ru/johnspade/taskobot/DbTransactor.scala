package ru.johnspade.taskobot

import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.DbConfig
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext

object DbTransactor:
  type DbTransactor = Transactor[Task]

  val live =
    ZLayer.scoped {
      def transactor(config: DbConfig, connectEc: ExecutionContext)(using rts: Runtime[Any]) = {
        val hikariConfig = new HikariConfig()
        hikariConfig.setDriverClassName(config.driver)
        hikariConfig.setJdbcUrl(config.url)
        hikariConfig.setUsername(config.user)
        hikariConfig.setPassword(config.password)

        HikariTransactor.fromHikariConfig[Task](
          hikariConfig,
          connectEc
        )
      }

      (ZIO
        .runtime[Any]
        .flatMap { implicit rts =>
          for
            dbConfig   <- ZIO.service[DbConfig]
            connectEc  <- ZIO.blocking(ZIO.descriptor.map(_.executor.asExecutionContext))
            transactor <- transactor(dbConfig, connectEc).toScopedZIO
          yield transactor
        })
        .orDie
    }
