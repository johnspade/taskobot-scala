package ru.johnspade.taskobot.task

import cats.effect.ConcurrentEffect
import cats.implicits._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.{Chats, CheckTask, ConfirmTask, Page, Tasks}
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.DoneAt
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags.UserId
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{BotService, CbDataRoutes, CbDataUserRoutes, DefaultPageSize, Errors, Keyboards, Messages}
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl._
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageReplyMarkup, editMessageText, sendMessage}
import telegramium.bots.high._
import telegramium.bots.high.implicits._
import telegramium.bots.{ChatIntId, Message}
import zio._
import zio.clock.Clock
import zio.interop.catz._

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
    override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of {

      case ConfirmTask(taskIdOpt, senderIdOpt) in cb =>
        def confirm(task: BotTask, from: User): Task[Option[Method[_]]] =
          for {
            _ <- taskRepo.setReceiver(task.id, senderIdOpt, from.id)
            _ <- editMessageReplyMarkup(inlineMessageId = cb.inlineMessageId, replyMarkup = Option.empty)
              .exec
              .fork
          } yield answerCallbackQuery(cb.id).some

        def mustBeConfirmedByReceiver(from: User): UIO[Option[Method[_]]] = {
          implicit val languageId: LanguageId = LanguageId(from.language.value)
          ZIO.succeed(answerCallbackQuery(cb.id, t"The task must be confirmed by the receiver".some).some)
        }

        for {
          id <- ZIO.fromOption(taskIdOpt).orElseFail(new RuntimeException(Errors.Default))
          taskOpt <- taskRepo.findById(id)
          task <- ZIO.fromOption(taskOpt).orElseFail(new RuntimeException(Errors.NotFound))
          user <- botService.updateUser(cb.from)
          answerOpt <- if (task.sender == UserId(cb.from.id)) mustBeConfirmedByReceiver(user) else confirm(task, user)
        } yield answerOpt

    }

    override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of {

      case Chats(pageNumber) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.value)
        for {
          page <- Page.request[User, Task](pageNumber, DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id))
          _ <- ZIO.foreach_(cb.message) { msg =>
            editMessageText(
              ChatIntId(msg.chat.id).some,
              msg.messageId.some,
              text = Messages.chatsWithTasks(),
              replyMarkup = Keyboards.chats(page, user).some
            )
              .exec[Task]
          }
          ack = answerCallbackQuery(cb.id).some
        } yield ack

      case Tasks(pageNumber, collaboratorId) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.value)
        for {
          userOpt <- userRepo.findById(collaboratorId)
          collaborator <- ZIO.fromOption(userOpt).orElseFail(new RuntimeException(Errors.NotFound))
          message <- ZIO.fromOption(cb.message).orElseFail(new RuntimeException(Errors.Default))
          (page, messageEntities) <- botService.getTasks(user, collaborator, pageNumber, message)
          _ <- listTasks(message, messageEntities, page, collaborator)
        } yield answerCallbackQuery(cb.id).some

      case CheckTask(pageNumber, id) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.value)

        def checkTask(task: TaskWithCollaborator) =
          for {
            now <- clock.instant
            _ <- taskRepo.check(task.id, DoneAt(now.toEpochMilli), user.id)
          } yield ()

        def listTasksAndNotify(task: TaskWithCollaborator, message: Message) =
          task.collaborator.map { collaborator =>
            for {
              (page, messageEntities) <- botService.getTasks(user, collaborator, pageNumber, message)
              _ <- listTasks(message, messageEntities, page, collaborator)
              _ <- notify(task, user, collaborator).when(user.id != collaborator.id)
            } yield ()
          }
            .getOrElse(Task.unit)

        for {
          taskOpt <- taskRepo.findByIdWithCollaborator(id, user.id)
          task <- ZIO.fromEither(taskOpt.toRight(new RuntimeException(Errors.NotFound)))
          message <- ZIO.fromEither(cb.message.toRight(new RuntimeException(Errors.Default)))
          _ <- checkTask(task)
          _ <- listTasksAndNotify(task, message).fork
          answerText = t"Task has been marked as completed."
        } yield answerCallbackQuery(cb.id, answerText.some).some

    }

    private def notify(task: TaskWithCollaborator, from: User, collaborator: User) =
      collaborator.chatId.map { chatId =>
        implicit val languageId: LanguageId = LanguageId(collaborator.language.value)
        val taskText = task.text
        val completedBy = from.fullName
        sendMessage(
          ChatIntId(chatId),
          t"""Task "$taskText" has been marked as completed by $completedBy."""
        )
          .exec
          .void
      }
        .getOrElse(Task.unit)

    private def listTasks(message: Message, messageEntities: List[TypedMessageEntity], page: Page[BotTask], collaborator: User)(
      implicit languageId: LanguageId
    ) =
      editMessageText(
        ChatIntId(message.chat.id).some,
        message.messageId.some,
        text = messageEntities.map(_.text).mkString,
        entities = TypedMessageEntity.toMessageEntities(messageEntities),
        replyMarkup = Keyboards.tasks(page, collaborator).some
      )
        .exec
        .void

  }
}
