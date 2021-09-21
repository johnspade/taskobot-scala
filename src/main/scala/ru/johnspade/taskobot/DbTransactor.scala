package ru.johnspade.taskobot

import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.Configuration.DbConfig
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object DbTransactor {
  type DbTransactor = Has[Transactor[Task]]

  val live: URLayer[Has[DbConfig] with Blocking with Clock, DbTransactor] =
    ZLayer.fromServicesManaged[DbConfig, Blocking.Service, Clock with Blocking, Nothing, Transactor[Task]] {
      (db, blocking) =>
        def transactor(config: DbConfig, connectEc: ExecutionContext)(implicit rts: Runtime[Clock with Blocking]) = {
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

        (for {
          implicit0(rts: Runtime[Clock with Blocking]) <- ZIO.runtime[Clock with Blocking].toManaged_
          connectEc <- blocking.blocking(ZIO.descriptor.map(_.executor.asEC)).toManaged_
          transactor <- transactor(db, connectEc).toManagedZIO
        } yield transactor).orDie
    }
}
