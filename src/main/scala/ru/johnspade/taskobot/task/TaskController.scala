package ru.johnspade.taskobot.task

import cats.implicits.*
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.{Chats, CheckTask, ConfirmTask, Page, Tasks}
import ru.johnspade.taskobot.messages.{Language, MessageService}
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{BotService, CbDataRoutes, CbDataUserRoutes, DefaultPageSize, Errors, KeyboardService}
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.taskobot.messages.MsgId
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageReplyMarkup, editMessageText, sendMessage}
import telegramium.bots.high.*
import telegramium.bots.high.implicits.*
import telegramium.bots.{ChatIntId, Message}
import zio.*
import zio.interop.catz.*

trait TaskController:
  def routes: CbDataRoutes[Task]

  def userRoutes: CbDataUserRoutes[Task]

final class TaskControllerLive(
    userRepo: UserRepository,
    taskRepo: TaskRepository,
    botService: BotService,
    msgService: MessageService,
    kbService: KeyboardService
)(implicit
    api: Api[Task]
) extends TaskController:
  override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of { case ConfirmTask(taskIdOpt, senderIdOpt) in cb =>
    def confirm(task: BotTask, from: User): Task[Option[Method[_]]] =
      for
        _ <- taskRepo.setReceiver(task.id, senderIdOpt, from.id)
        _ <- editMessageReplyMarkup(inlineMessageId = cb.inlineMessageId, replyMarkup = Option.empty).exec
      yield answerCallbackQuery(cb.id).some

    def mustBeConfirmedByReceiver(from: User): UIO[Option[Method[_]]] = {
      ZIO.succeed(
        answerCallbackQuery(
          cb.id,
          msgService.getMessage(MsgId.`tasks-must-be-confirmed`, from.language).some
        ).some
      )
    }

    for
      id        <- ZIO.fromOption(taskIdOpt).orElseFail(new RuntimeException(Errors.Default))
      taskOpt   <- taskRepo.findById(id)
      task      <- ZIO.fromOption(taskOpt).orElseFail(new RuntimeException(Errors.NotFound))
      user      <- botService.updateUser(cb.from)
      answerOpt <- if (task.sender == cb.from.id) mustBeConfirmedByReceiver(user) else confirm(task, user)
    yield answerOpt

  }

  override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of {

    case Chats(pageNumber) in cb as user =>
      for
        page <- Page.request[User, Task](pageNumber, DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id))
        _ <- ZIO.foreachDiscard(cb.message) { msg =>
          editMessageText(
            ChatIntId(msg.chat.id).some,
            msg.messageId.some,
            text = msgService.chatsWithTasks(user.language),
            replyMarkup = kbService.chats(page, user).some
          )
            .exec[Task]
        }
        ack = answerCallbackQuery(cb.id).some
      yield ack

    case Tasks(pageNumber, collaboratorId) in cb as user =>
      for
        userOpt            <- userRepo.findById(collaboratorId)
        collaborator       <- ZIO.fromOption(userOpt).orElseFail(new RuntimeException(Errors.NotFound))
        message            <- ZIO.fromOption(cb.message).orElseFail(new RuntimeException(Errors.Default))
        pageAndMsgEntities <- botService.getTasks(user, collaborator, pageNumber)
        _ <- listTasks(message, pageAndMsgEntities._2, pageAndMsgEntities._1, collaborator, user.language)
      yield answerCallbackQuery(cb.id).some

    case CheckTask(pageNumber, id) in cb as user =>
      def checkTask(task: TaskWithCollaborator) =
        for
          now <- Clock.instant
          _   <- taskRepo.check(task.id, now.toEpochMilli, user.id)
        yield ()

      def listTasksAndNotify(task: TaskWithCollaborator, message: Message) =
        task.collaborator
          .map { collaborator =>
            for
              pageAndMsgEntities <- botService.getTasks(user, collaborator, pageNumber)
              _ <- listTasks(message, pageAndMsgEntities._2, pageAndMsgEntities._1, collaborator, user.language)
              _ <- notify(task, user, collaborator).when(user.id != collaborator.id)
            yield ()
          }
          .getOrElse(Task.unit)

      taskRepo
        .findByIdWithCollaborator(id, user.id)
        .flatMap { taskOpt =>
          val answerText =
            (for
              _ <- taskOpt.toRight(Errors.NotFound)
              _ <- cb.message.toRight(Errors.Default)
            yield msgService.getMessage(MsgId.`tasks-completed`, user.language)).merge

          ZIO
            .collectAllDiscard {
              for
                task    <- taskOpt
                message <- cb.message
              yield checkTask(task) *>
                listTasksAndNotify(task, message)
            }
            .as(answerCallbackQuery(cb.id, answerText.some).some)
        }

  }

  private def notify(task: TaskWithCollaborator, from: User, collaborator: User) =
    collaborator.chatId
      .map { chatId =>
        val taskText    = task.text
        val completedBy = from.fullName
        sendMessage(
          ChatIntId(chatId),
          msgService.getMessage(MsgId.`tasks-completed-by`, from.language, taskText, completedBy)
        ).exec.void
      }
      .getOrElse(Task.unit)

  private def listTasks(
      message: Message,
      messageEntities: List[TypedMessageEntity],
      page: Page[BotTask],
      collaborator: User,
      language: Language
  ) =
    editMessageText(
      ChatIntId(message.chat.id).some,
      message.messageId.some,
      text = messageEntities.map(_.text).mkString,
      entities = TypedMessageEntity.toMessageEntities(messageEntities),
      replyMarkup = kbService.tasks(page, collaborator, language).some
    ).exec.void

object TaskControllerLive:
  val layer: URLayer[
    UserRepository with TaskRepository with BotService with MessageService with KeyboardService with TelegramBotApi,
    TaskController
  ] =
    ZLayer(
      for
        userRepo   <- ZIO.service[UserRepository]
        taskRepo   <- ZIO.service[TaskRepository]
        botService <- ZIO.service[BotService]
        msgService <- ZIO.service[MessageService]
        kbService  <- ZIO.service[KeyboardService]
        api        <- ZIO.service[TelegramBotApi]
      yield TaskControllerLive(userRepo, taskRepo, botService, msgService, kbService)(api)
    )
