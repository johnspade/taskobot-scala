package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.Configuration
import ru.johnspade.taskobot.Taskobot.Taskobot
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.task.{TaskController, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository
import zio.{ULayer, URLayer, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

object Environments {
  type AppEnvironment = Blocking with Clock with Configuration with Taskobot

  private val logger: ULayer[Logging] = Slf4jLogger.make((_, s) => s)

  private val dbConfig = Configuration.liveDbConfig
  private val sessionPool = dbConfig >>> SessionPool.live
  private val userRepo = sessionPool >>> UserRepository.live
  private val taskRepo = sessionPool >>> TaskRepository.live
  private val repositories = userRepo ++ taskRepo
  private val botConfig = Configuration.liveBotConfig
  private val botApi = botConfig >>> TelegramBotApi.live
  private val botService = repositories >>> BotService.live
  private val commandController =
    (ZLayer.requires[Clock] ++ botApi ++ botService ++ repositories) >>> CommandController.live
  private val taskController = (repositories ++ botService ++ botApi ++ ZLayer.requires[Clock] ++ logger) >>> TaskController.live
  private val settingsController = (userRepo ++ botApi) >>> SettingsController.live
  private val userMiddleware = botService >>> UserMiddleware.live
  private val taskobot = (
    ZLayer.requires[Clock] ++
      botApi ++
      botConfig ++
      botService ++
      repositories ++
      commandController ++
      taskController ++
      settingsController ++
      userMiddleware ++
      logger
    ) >>>
    Taskobot.live

  val appEnvironment: URLayer[Blocking with Clock, AppEnvironment] =
    ZLayer.requires[Blocking with Clock] >+> (botConfig ++ dbConfig ++ taskobot)
}
