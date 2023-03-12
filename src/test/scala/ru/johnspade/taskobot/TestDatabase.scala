package ru.johnspade.taskobot

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import zio.*

object TestDatabase:
  private val container: ULayer[PostgreSQLContainer] =
    ZLayer.scoped(
      ZIO.acquireRelease {
        ZIO.attemptBlocking {
          val container = new PostgreSQLContainer(
            dockerImageNameOverride = Some(DockerImageName.parse("postgres:12.6"))
          )
          container.start()
          container
        }.orDie
      }(container => ZIO.attemptBlocking(container.stop()).orDie)
    )

  private val dbConfig: URLayer[PostgreSQLContainer, DbConfig] =
    ZLayer(
      ZIO
        .service[PostgreSQLContainer]
        .map { container =>
          DbConfig(
            "org.postgresql.Driver",
            container.jdbcUrl,
            container.username,
            container.password
          )
        }
    )

  val layer: ULayer[DbTransactor] =
    ZLayer.make[DbTransactor](
      TestDatabase.container,
      TestDatabase.dbConfig,
      DbTransactor.live,
      ZLayer(FlywayMigration.migrate.orDie)
    )
