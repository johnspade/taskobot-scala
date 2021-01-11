package ru.johnspade.taskobot.task

import cats.effect.ConcurrentEffect
import cats.implicits._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryDsl._
import ru.johnspade.taskobot.core.{CbData, Chats, CheckTask, ConfirmTask, Page, Tasks, TypedMessageEntity}
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.DoneAt
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{BotService, CbDataRoutes, CbDataUserRoutes, DefaultPageSize, Keyboards, Messages}
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.{ChatIntId, Message}
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageReplyMarkup, editMessageText, sendMessage}
import telegramium.bots.high._
import telegramium.bots.high.implicits._
import zio._
import zio.clock.Clock
import zio.interop.catz._
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.user.tags.UserId
import ru.makkarpov.scalingua.I18n._
import telegramium.bots.client.Method

object TaskController {
  type TaskController = Has[Service]

  trait Service {
    def routes: CbDataRoutes[Task]

    def userRoutes: CbDataUserRoutes[Task]
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
    override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of[CbData, Task] {

      case ConfirmTask(senderId, taskIdOpt) in cb =>
        def confirm(task: BotTask): UIO[Option[Method[_]]] =
          for {
            user <- botService.updateUser(cb.from)
            _ <- taskRepo.setReceiver(task.id, senderId, user.id)
            _ <- editMessageReplyMarkup(inlineMessageId = cb.inlineMessageId, replyMarkup = Option.empty)
              .exec
              .orDie
              .fork
          } yield answerCallbackQuery(cb.id).some

        val mustBeConfirmedByReceiver: UIO[Option[Method[_]]] =
          (for {
            userOpt <- userRepo.findById(UserId(cb.from.id))
            user <- ZIO.fromOption(userOpt)
            implicit0(languageId: LanguageId) = LanguageId(user.language.languageTag)
            answer = answerCallbackQuery(cb.id, t"The task must be confirmed by the receiver".some)
          } yield answer)
            .optional

        (for {
          id <- ZIO.fromOption(taskIdOpt)
          taskOpt <- taskRepo.findById(id)
          task <- ZIO.fromOption(taskOpt)
          answerOpt <- if (task.sender == UserId(cb.from.id)) mustBeConfirmedByReceiver else confirm(task)
        } yield answerOpt)
          .optional
          .map(_.flatten)

    }

    override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of[CbData, User, Task] {

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
        (for {
          userOpt <- userRepo.findById(collaboratorId)
          collaborator <- ZIO.fromOption(userOpt)
          message <- ZIO.fromOption(cb.message)
          (page, messageEntities) <- botService.getTasks(user, collaborator, pageNumber, message)
          _ <- listTasks(message, messageEntities, page, collaborator)
        } yield answerCallbackQuery(cb.id))
          .optional

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
                (page, messageEntities) <- botService.getTasks(user, collaborator, pageNumber, message)
                _ <- listTasks(message, messageEntities, page, collaborator)
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
          t"""Task "$taskText" has been marked as completed by $completedBy."""
        )
          .exec
          .orDie
          .void
      }

    private def listTasks(message: Message, messageEntities: List[TypedMessageEntity], page: Page[BotTask], collaborator: User)(
      implicit languageId: LanguageId
    ) =
      editMessageText(
        ChatIntId(message.chat.id).some,
        message.messageId.some,
        text = messageEntities.map(_.value).mkString,
        entities = TypedMessageEntity.toMessageEntities(messageEntities),
        replyMarkup = Keyboards.tasks(page, collaborator).some
      )
        .exec
        .orDie
        .void

  }
}
