package ru.johnspade.taskobot

import ru.johnspade.taskobot.Configuration.Configuration
import ru.johnspade.taskobot.Taskobot.Taskobot
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.user.UserRepository
import zio.URLayer
import zio.blocking.Blocking

object Environments {
  type AppEnvironment = Blocking with Configuration with Taskobot

  private val dbConfig = Configuration.liveDbConfig
  private val sessionPool = dbConfig >>> SessionPool.live
  private val repositories = sessionPool >>> (UserRepository.live ++ TaskRepository.live)
  private val botConfig = Configuration.liveBotConfig
  private val botService = repositories >>> BotService.live
  private val commandController = botService >>> CommandController.live
  private val taskobot = (botConfig ++ botService ++ repositories ++ commandController) >>> Taskobot.live

  val appEnvironment: URLayer[Blocking, AppEnvironment] =
    botConfig ++ dbConfig ++ Blocking.live ++ taskobot
}
