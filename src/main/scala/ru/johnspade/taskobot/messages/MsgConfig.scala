package ru.johnspade.taskobot.messages

import zio.ULayer
import zio.ZIO
import zio.ZLayer

import pureconfig.CollectionReaders.mapReader
import pureconfig.ConfigReader
import pureconfig.ConfigSource

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
