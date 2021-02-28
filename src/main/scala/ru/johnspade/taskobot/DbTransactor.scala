package ru.johnspade.taskobot

import cats.effect.Blocker
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.Configuration.DbConfig
import zio._
import zio.blocking.Blocking
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object DbTransactor {
  type DbTransactor = Has[Transactor[Task]]

  val live: URLayer[Has[DbConfig] with Blocking, DbTransactor] =
    ZLayer.fromServicesManaged[DbConfig, Blocking.Service, Any, Nothing, Transactor[Task]] {
      (db, blocking) =>
        def transactor(config: DbConfig, connectEc: ExecutionContext, transactEc: ExecutionContext) = {
          val hikariConfig = new HikariConfig()
          hikariConfig.setDriverClassName(config.driver)
          hikariConfig.setJdbcUrl(config.url)
          hikariConfig.setUsername(config.user)
          hikariConfig.setPassword(config.password)

          HikariTransactor.fromHikariConfig[Task](
            hikariConfig,
            connectEc,
            Blocker.liftExecutionContext(transactEc)
          )
        }

        (for {
          connectEc <- ZIO.descriptor.map(_.executor.asEC).toManaged_
          transactEc <- blocking.blocking(ZIO.descriptor.map(_.executor.asEC)).toManaged_
          transactor <- transactor(db, connectEc, transactEc).toManagedZIO
        } yield transactor).orDie
    }
}
