package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.Configuration
import zio.{URLayer, ZLayer}
import zio.blocking.Blocking

object Environments {
  type AppEnvironment = Blocking with Configuration
  val appEnvironment: URLayer[Blocking, AppEnvironment] =
    ZLayer.requires[Blocking] >+> (Configuration.liveBotConfig ++ Configuration.liveDbConfig)
}
