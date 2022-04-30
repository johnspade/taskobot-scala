package ru.johnspade.taskobot

import java.net.URI

import pureconfig.{ConfigReader, ConfigSource}
import zio.*

final case class DbConfig(driver: String, url: String, user: String, password: String)
object DbConfig:
  implicit val dbConfigReader: ConfigReader[DbConfig] = ConfigReader.fromCursor[DbConfig] { config =>
    for
      obj <- config.asObjectCursor
      fluent = obj.fluent
      driver <- fluent.at("driver").asString
      url    <- fluent.at("url").asString
      dbUri    = new URI(url)
      userInfo = dbUri.getUserInfo.split(":")
    yield DbConfig(
      driver,
      s"jdbc:postgresql://${dbUri.getHost}:${dbUri.getPort}${dbUri.getPath}",
      user = userInfo(0),
      password = userInfo(1)
    )
  }

  val live: ULayer[DbConfig] = ZLayer(
    ZIO.attempt {
      ConfigSource.default.at("db").loadOrThrow[DbConfig]
    }.orDie
  )

final case class BotConfig(port: Int, url: String, token: String)
object BotConfig:
  implicit val botConfigReader: ConfigReader[BotConfig] =
    ConfigReader.forProduct3[BotConfig, Int, String, String]("port", "url", "token") { case (port, url, token) =>
      BotConfig(port, url, token)
    }
  val live: ULayer[BotConfig] = ZLayer(
    ZIO.attempt {
      ConfigSource.default.at("bot").loadOrThrow[BotConfig]
    }.orDie
  )
