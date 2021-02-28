package ru.johnspade.taskobot

import java.net.URI

import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.module.magnolia.semiauto.reader.deriveReader
import zio.{Has, ULayer, ZIO}

object Configuration {
  type Configuration = Has[DbConfig] with Has[BotConfig]

  final case class DbConfig(driver: String, url: String, user: String, password: String)
  object DbConfig {
    implicit val dbConfigReader: ConfigReader[DbConfig] = ConfigReader.fromCursor[DbConfig] { config =>
      for {
        obj <- config.asObjectCursor
        fluent = obj.fluent
        driver <- fluent.at("driver").asString
        url <- fluent.at("url").asString
        dbUri = new URI(url)
        userInfo = dbUri.getUserInfo.split(":")
      } yield DbConfig(
        driver,
        s"jdbc:postgresql://${dbUri.getHost}:${dbUri.getPort}${dbUri.getPath}",
        user = userInfo(0),
        password = userInfo(1)
      )
    }
  }

  final case class BotConfig(port: Int, url: String, token: String)
  object BotConfig {
    implicit val botConfigReader: ConfigReader[BotConfig] = deriveReader[BotConfig]
  }

  val liveDbConfig: ULayer[Has[DbConfig]] = ZIO.effect {
    ConfigSource.default.at("db").loadOrThrow[DbConfig]
  }
    .toLayer
    .orDie

  val liveBotConfig: ULayer[Has[BotConfig]] = ZIO.effect {
    ConfigSource.default.at("bot").loadOrThrow[BotConfig]
  }
    .toLayer
    .orDie
}
