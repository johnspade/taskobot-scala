package ru.johnspade.taskobot

import java.net.URI

import zio.*

import pureconfig.ConfigReader
import pureconfig.ConfigSource

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

final case class BotConfig(port: Int, url: String, token: String, username: String)
object BotConfig:
  implicit val botConfigReader: ConfigReader[BotConfig] =
    ConfigReader.forProduct4[BotConfig, Int, String, String, String]("port", "url", "token", "username") {
      case (port, url, token, username) =>
        BotConfig(port, url, token, username)
    }
  val live: ULayer[BotConfig] = ZLayer(
    ZIO.attempt {
      ConfigSource.default.at("bot").loadOrThrow[BotConfig]
    }.orDie
  )
