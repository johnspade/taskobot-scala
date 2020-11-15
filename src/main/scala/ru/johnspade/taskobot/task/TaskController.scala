package ru.johnspade.taskobot.task

import cats.effect.ConcurrentEffect
import cats.implicits._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryDsl._
import ru.johnspade.taskobot.core.{CbData, Chats, Page, Tasks}
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{BotService, CbDataUserRoutes, DefaultPageSize, Keyboards, Messages}
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.ChatIntId
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageText}
import telegramium.bots.high._
import telegramium.bots.high.implicits._
import zio._
import zio.interop.catz._

object TaskController {
  type TaskController = Has[Service]

  trait Service {
    def routes: CbDataUserRoutes[Task]
  }

  val live: URLayer[UserRepository with TaskRepository with BotService with TelegramBotApi, TaskController] =
    ZLayer.fromServicesM[
      UserRepository.Service,
      TaskRepository.Service,
      BotService.Service,
      Api[Task],
      Any,
      Nothing,
      TaskController.Service
    ] {
      (userRepo, taskRepo, botService, api) =>
        ZIO.concurrentEffect.map { implicit CE: ConcurrentEffect[Task] =>
          new LiveTaskController(userRepo, taskRepo, botService)(api, CE)
        }
    }

  final class LiveTaskController(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service,
    botService: BotService.Service
  )(
    implicit api: Api[Task],
    CE: ConcurrentEffect[Task]
  ) extends Service {
    override val routes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of[CbData, User, Task] {

      case Chats(pageNumber) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        for {
          page <- Page.request[User, UIO](pageNumber, DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id))
          _ <- ZIO.foreach_(cb.message) { msg =>
            editMessageText(
              ChatIntId(msg.chat.id).some,
              msg.messageId.some,
              text = Messages.chatsWithTasks(),
              replyMarkup = Keyboards.chats(page, user).some
            )
              .exec
              .orDie
          }
          ack = answerCallbackQuery(cb.id).some
        } yield ack

      case Tasks(collaboratorId, pageNumber) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        userRepo.findById(collaboratorId).flatMap { userOpt =>
          ZIO.foreach(
            for {
              collaborator <- userOpt
              message <- cb.message
            } yield {
              botService.listTasks(collaborator, collaborator, pageNumber, message)
                .as(answerCallbackQuery(cb.id))
            }
          )(identity)
        }

    }
  }
}
