package ru.johnspade.taskobot

import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder

final case class TimezonesWebAppData(timezone: String)

object TimezonesWebAppData:
  given JsonDecoder[TimezonesWebAppData] = DeriveJsonDecoder.gen
