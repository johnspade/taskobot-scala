package ru.johnspade.taskobot.task

import cats.syntax.option.*
import ru.johnspade.taskobot.BotService
import ru.johnspade.taskobot.CbDataRoutes
import ru.johnspade.taskobot.CbDataUserRoutes
import ru.johnspade.taskobot.DefaultPageSize
import ru.johnspade.taskobot.Errors
import ru.johnspade.taskobot.KeyboardService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.Chats
import ru.johnspade.taskobot.core.CheckTask
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.DatePicker
import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.RemoveTaskDeadline
import ru.johnspade.taskobot.core.TaskDeadlineDate
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.Tasks
import ru.johnspade.taskobot.core.TelegramOps.ackCb
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.Message
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.*
import telegramium.bots.high.*
import telegramium.bots.high.implicits.*
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
      taskOpt   <- taskRepo.findByIdUnsafe(id)
      task      <- ZIO.fromOption(taskOpt).orElseFail(new RuntimeException(Errors.NotFound))
      user      <- botService.updateUser(cb.from)
      answerOpt <- if (task.sender == cb.from.id) mustBeConfirmedByReceiver(user) else confirm(task, user)
    yield answerOpt

  }

  override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of {

    case Chats(pageNumber) in cb as user =>
      ackCb(cb) { msg =>
        for
          page <- Page.request[User, Task](pageNumber, DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id))
          _ <- editMessageText(
            msgService.chatsWithTasks(user.language),
            ChatIntId(msg.chat.id).some,
            msg.messageId.some,
            replyMarkup = kbService.chats(page, user).some
          )
            .exec[Task]
        yield ()
      }

    case Tasks(pageNumber, collaboratorId) in cb as user =>
      ackCb(cb) { msg =>
        for
          userOpt            <- userRepo.findById(collaboratorId)
          collaborator       <- ZIO.fromOption(userOpt).orElseFail(new RuntimeException(Errors.NotFound))
          pageAndMsgEntities <- botService.getTasks(user, collaborator, pageNumber)
          _ <- listTasks(msg, pageAndMsgEntities._2, pageAndMsgEntities._1, collaborator, user.language)
        yield ()
      }

    case TaskDetails(id, pageNumber) in cb as user =>
      returnTaskDetails(cb, user, id, pageNumber, task => ZIO.succeed(task))

    case CheckTask(pageNumber, id) in cb as user =>
      def checkTask(task: TaskWithCollaborator) =
        for
          now <- Clock.instant
          _   <- taskRepo.check(task.id, now, user.id)
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
          .getOrElse(ZIO.unit)

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

    case TaskDeadlineDate(id, date) in cb as user =>
      def setDeadline(task: BotTask) =
        val deadline = task.deadline
          .map(dt => dt.`with`(date.atTime(dt.toLocalTime())))
          .getOrElse(date.atStartOfDay())
        taskRepo.setDeadline(id, Some(deadline), user.id)

      returnTaskDetails(cb, user, id, pageNumber = 0, processTask = setDeadline)

    case RemoveTaskDeadline(id) in cb as user =>
      returnTaskDetails(
        cb,
        user,
        id,
        pageNumber = 0,
        processTask = task => taskRepo.setDeadline(task.id, deadline = None, userId = user.id)
      )

    case TimePicker(taskId, Some(hour), Some(minute), true) in cb as user =>
      def setDeadline(task: BotTask) =
        task.deadline
          .map { dt =>
            val deadline = dt
              .withHour(hour)
              .withMinute(minute)
            taskRepo.setDeadline(taskId, Some(deadline), user.id)
          }
          .getOrElse(ZIO.succeed(task))
      returnTaskDetails(cb, user, taskId, pageNumber = 0, processTask = setDeadline)
  }

  private def notify(task: TaskWithCollaborator, from: User, collaborator: User) =
    collaborator.chatId
      .map { chatId =>
        val taskText    = task.text
        val completedBy = from.fullName
        sendMessage(
          ChatIntId(chatId),
          msgService.getMessage(MsgId.`tasks-completed-by`, from.language, taskText, completedBy)
        ).exec.unit
      }
      .getOrElse(ZIO.unit)

  private def listTasks(
      message: Message,
      messageEntities: List[TypedMessageEntity],
      page: Page[BotTask],
      collaborator: User,
      language: Language
  ) =
    editMessageText(
      messageEntities.map(_.text).mkString,
      ChatIntId(message.chat.id).some,
      message.messageId.some,
      entities = TypedMessageEntity.toMessageEntities(messageEntities),
      replyMarkup = kbService.tasks(page, collaborator, language).some
    ).exec.unit

  private def editWithTaskDetails(
      message: Message,
      messageEntities: List[TypedMessageEntity],
      taskId: Long,
      pageNumber: Int,
      collaborator: User
  ) =
    Clock.instant.flatMap { now =>
      editMessageText(
        messageEntities.map(_.text).mkString,
        ChatIntId(message.chat.id).some,
        message.messageId.some,
        entities = TypedMessageEntity.toMessageEntities(messageEntities),
        replyMarkup = Some(
          InlineKeyboardMarkup(
            List(
              List(inlineKeyboardButton("✅", CheckTask(0, taskId))),
              List(
                inlineKeyboardButton(
                  "📅",
                  DatePicker(taskId, now.atZone(collaborator.timezoneOrDefault).toLocalDate())
                ),
                inlineKeyboardButton("🕒", TimePicker(taskId))
              ),
              List(
                inlineKeyboardButton(
                  msgService.getMessage(MsgId.`tasks`, collaborator.language),
                  Tasks(pageNumber, collaborator.id)
                )
              )
            )
          )
        )
      ).exec.unit
    }

  private def returnTaskDetails(
      cb: CallbackQuery,
      user: User,
      id: Long,
      pageNumber: Int,
      processTask: BotTask => Task[BotTask]
  ) =
    ackCb(cb) { msg =>
      for
        taskOpt <- taskRepo.findById(id, user.id)
        task    <- ZIO.fromOption(taskOpt).orElseFail(new RuntimeException(Errors.NotFound))
        task    <- processTask(task)
        messageEntities = botService.createTaskDetails(task, user.language)
        collaboratorId  = task.getCollaborator(user.id)
        collaborator <- if collaboratorId == user.id then ZIO.some(user) else userRepo.findById(collaboratorId)
        _ <- editWithTaskDetails(
          msg,
          messageEntities,
          task.id,
          pageNumber = pageNumber,
          collaborator = collaborator.getOrElse(user)
        )
      yield ()
    }

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
