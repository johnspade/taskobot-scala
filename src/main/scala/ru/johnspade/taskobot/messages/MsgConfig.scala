package ru.johnspade.taskobot.messages

import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.CollectionReaders.mapReader
import zio.{ULayer, ZIO, ZLayer}

case class MsgConfig(messages: Map[Language, Map[MsgId, String]])

object MsgConfig:
  given ConfigReader[MsgConfig] = mapReader[Map[String, String]].map { m =>
    MsgConfig(
      Language.values
        .map(language => language -> m(language.value))
        .toMap
        .view
        .mapValues { langMessages =>
          MsgId.values.map(id => id -> langMessages(id.toString)).toMap
        }
        .toMap
    )
  }

  val live: ULayer[MsgConfig] = ZLayer(
    ZIO.attempt {
      ConfigSource.default.at("messages").loadOrThrow[MsgConfig]
    }.orDie
  )
