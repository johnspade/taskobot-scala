package ru.johnspade.taskobot.task

import cats.effect.ConcurrentEffect
import cats.implicits._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryDsl._
import ru.johnspade.taskobot.core.{CbData, Chats, CheckTask, Page, Tasks}
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.DoneAt
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{BotService, CbDataUserRoutes, DefaultPageSize, Keyboards, Messages}
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.{ChatIntId, Message}
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageText, sendMessage}
import telegramium.bots.high._
import telegramium.bots.high.implicits._
import zio._
import zio.clock.Clock
import zio.interop.catz._
import ru.johnspade.taskobot.i18n.messages
import ru.makkarpov.scalingua.I18n._

object TaskController {
  type TaskController = Has[Service]

  trait Service {
    def routes: CbDataUserRoutes[Task]
  }

  val live: URLayer[UserRepository with TaskRepository with BotService with TelegramBotApi with Clock, TaskController] =
    ZLayer.fromServicesM[
      UserRepository.Service,
      TaskRepository.Service,
      BotService.Service,
      Api[Task],
      Clock.Service,
      Any,
      Nothing,
      TaskController.Service
    ] {
      (userRepo, taskRepo, botService, api, clock) =>
        ZIO.concurrentEffect.map { implicit CE: ConcurrentEffect[Task] =>
          new LiveTaskController(userRepo, taskRepo, botService, clock)(api, CE)
        }
    }

  final class LiveTaskController(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service,
    botService: BotService.Service,
    clock: Clock.Service
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
          (for {
            collaborator <- userOpt
            message <- cb.message
          } yield {
            botService.listTasks(collaborator, collaborator, pageNumber, message)
              .as(answerCallbackQuery(cb.id))
          }
            ).sequence
        }

      case CheckTask(id, pageNumber, collaboratorId) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)

        def checkTask(task: BotTask) =
          for {
            now <- clock.instant
            _ <- taskRepo.check(task.id, DoneAt(now.toEpochMilli), user.id)
          } yield ()

        def withCollaborator(task: BotTask, message: Message) =
          userRepo.findById(collaboratorId).flatMap { userOpt =>
            userOpt.fold(ZIO.unit) { collaborator =>
              for {
                _ <- botService.listTasks(user, collaborator, pageNumber, message)
                _ <- notify(task, user, collaborator).when(user.id != collaboratorId)
              } yield ()
            }
          }

        (for {
          taskOpt <- taskRepo.findById(id)
          task <- ZIO.fromOption(taskOpt)
          message <- ZIO.fromOption(cb.message)
          _ <- checkTask(task)
          _ <- withCollaborator(task, message).fork
          answer = answerCallbackQuery(cb.id, t"Task has been marked as completed.".some)
        } yield answer)
          .optional
    }

    private def notify(task: BotTask, from: User, collaborator: User) =
      collaborator.chatId.fold(ZIO.unit) { chatId =>
        implicit val languageId: LanguageId = LanguageId(collaborator.language.languageTag)
        val taskText = task.text
        val completedBy = from.fullName
        sendMessage(
          ChatIntId(chatId),
          t"Task $taskText has been marked as completed by $completedBy."
        )
          .exec
          .orDie
          .void
      }
  }
}
