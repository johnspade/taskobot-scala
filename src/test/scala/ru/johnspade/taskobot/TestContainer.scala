package ru.johnspade.taskobot

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import ru.johnspade.taskobot.Configuration.DbConfig
import zio._
import zio.blocking.{Blocking, effectBlocking}

object TestContainer {
  type Postgres = Has[PostgreSQLContainer]

  val postgres: URLayer[Blocking, Postgres] =
    ZManaged.make {
      effectBlocking {
        val container = new PostgreSQLContainer(
          dockerImageNameOverride = Some(DockerImageName.parse("postgres"))
        )
        container.start()
        container
      }.orDie
    }(container => effectBlocking(container.stop()).orDie).toLayer

  val dbConfig: URLayer[Postgres, Has[DbConfig]] =
    ZLayer.fromService[PostgreSQLContainer, DbConfig] { container =>
      DbConfig(
        "org.postgresql.Driver",
        container.host,
        container.mappedPort(5432),
        container.databaseName,
        container.username,
        container.password
      )
    }

}
