package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.Configuration
import ru.johnspade.taskobot.Taskobot.Taskobot
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.task.{TaskController, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository
import zio.{URLayer, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock

object Environments {
  type AppEnvironment = Blocking with Clock with Configuration with Taskobot

  private val dbConfig = Configuration.liveDbConfig
  private val sessionPool = dbConfig >>> SessionPool.live
  private val userRepo = sessionPool >>> UserRepository.live
  private val taskRepo = sessionPool >>> TaskRepository.live
  private val repositories = userRepo ++ taskRepo
  private val botConfig = Configuration.liveBotConfig
  private val botApi = botConfig >>> TelegramBotApi.live
  private val botService = repositories ++ botApi >>> BotService.live
  private val commandController =
    (ZLayer.requires[Clock] ++ botApi ++ botService ++ repositories) >>> CommandController.live
  private val taskController = (repositories ++ botService ++ botApi ++ ZLayer.requires[Clock]) >>> TaskController.live
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
      userMiddleware
    ) >>>
    Taskobot.live

  val appEnvironment: URLayer[Blocking with Clock, AppEnvironment] =
    ZLayer.requires[Blocking with Clock] >+> (botConfig ++ dbConfig ++ taskobot)
}
