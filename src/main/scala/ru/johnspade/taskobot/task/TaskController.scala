package ru.johnspade.taskobot.task

import cats.syntax.option.*
import ru.johnspade.taskobot.BotService
import ru.johnspade.taskobot.CbDataRoutes
import ru.johnspade.taskobot.CbDataUserRoutes
import ru.johnspade.taskobot.DefaultPageSize
import ru.johnspade.taskobot.Errors
import ru.johnspade.taskobot.Errors.MaxRemindersExceeded
import ru.johnspade.taskobot.KeyboardService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.TelegramOps.*
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.core.*
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.Message
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.*
import telegramium.bots.high.*
import telegramium.bots.high.implicits.*
import telegramium.bots.high.messageentities.MessageEntities
import zio.*
import zio.interop.catz.*

trait TaskController:
  def routes: CbDataRoutes[Task]

  def userRoutes: CbDataUserRoutes[Task]

final class TaskControllerLive(
    userRepo: UserRepository,
    taskRepo: TaskRepository,
    reminderRepo: ReminderRepository,
    botService: BotService,
    msgService: MessageService,
    kbService: KeyboardService
)(using api: Api[Task])
    extends TaskController:
  override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of { case ConfirmTask(taskIdOpt, senderIdOpt) in cb =>
    def confirm(task: BotTask, from: User): Task[Option[Method[_]]] =
      for
        _ <- taskRepo.setReceiver(task.id, senderIdOpt, from.id)
        _ <- execDiscardWithHandling(
          editMessageReplyMarkup(inlineMessageId = cb.inlineMessageId, replyMarkup = Option.empty)
        )
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
          _ <- execDiscardWithHandling(
            editMessageText(
              msgService.chatsWithTasks(user.language),
              ChatIntId(msg.chat.id).some,
              msg.messageId.some,
              replyMarkup = kbService.chats(page, user).some
            )
          )
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

    case Reminders(taskId, pageNumber) in cb as user =>
      for
        task      <- getTaskSafe(taskId, user.id)
        reminders <- reminderRepo.getByTaskIdAndUserId(taskId, user.id)
        result <- taskDetails(
          cb,
          user,
          task,
          processTask = ZIO.succeed(_),
          generateKeyboard =
            (_, _) => ZIO.succeed(createRemindersKeyboard(taskId, reminders, pageNumber, language = user.language))
        )
      yield result

    case StandardReminders(taskId, pageNumber) in cb as user =>
      for
        task <- getTaskSafe(taskId, user.id)
        result <- taskDetails(
          cb,
          user,
          task,
          processTask = ZIO.succeed(_),
          generateKeyboard = (_, _) => ZIO.succeed(kbService.standardReminders(taskId, pageNumber, user.language))
        )
      yield result

    case CreateReminder(taskId, offsetMinutes) in cb as user =>
      for
        task <- getTaskSafe(taskId, user.id)
        _ <- reminderRepo
          .create(task.id, user.id, offsetMinutes)
          .when(task.deadline.isDefined)
          .catchSome { case MaxRemindersExceeded(taskId) =>
            ZIO.logWarning(s"Max reminders for taskId $taskId exceeded")
          }
        reminders <- reminderRepo.getByTaskIdAndUserId(task.id, user.id)
        result <- taskDetails(
          cb,
          user,
          task,
          processTask = ZIO.succeed(_),
          generateKeyboard =
            (_, _) => ZIO.succeed(createRemindersKeyboard(taskId, reminders, pageNumber = 0, language = user.language))
        )
      yield result

    case RemoveReminder(reminderId, taskId) in cb as user =>
      for
        task      <- getTaskSafe(taskId, user.id)
        _         <- reminderRepo.delete(reminderId, user.id)
        reminders <- reminderRepo.getByTaskIdAndUserId(task.id, user.id)
        result <- taskDetails(
          cb,
          user,
          task,
          processTask = ZIO.succeed(_),
          generateKeyboard =
            (_, _) => ZIO.succeed(createRemindersKeyboard(taskId, reminders, pageNumber = 0, language = user.language))
        )
      yield result
  }

  private def notify(task: TaskWithCollaborator, from: User, collaborator: User) =
    collaborator.chatId
      .map { chatId =>
        val taskText    = task.text
        val completedBy = from.fullName
        sendMessage(
          ChatIntId(chatId),
          msgService.getMessage(MsgId.`tasks-completed-by`, collaborator.language, taskText, completedBy)
        ).exec.unit
      }
      .getOrElse(ZIO.unit)

  private def listTasks(
      message: Message,
      messageEntities: MessageEntities,
      page: Page[BotTask],
      collaborator: User,
      language: Language
  ) =
    execDiscardWithHandling(
      editMessageText(
        messageEntities.toPlainText(),
        ChatIntId(message.chat.id).some,
        message.messageId.some,
        entities = messageEntities.toTelegramEntities(),
        replyMarkup = kbService.tasks(page, collaborator, language).some
      )
    )

  private def minutesToLabel(minutes: Int, language: Language): String =
    val days                      = minutes / (60 * 24)
    val remainingMinutesAfterDays = minutes                   % (60 * 24)
    val hours                     = remainingMinutesAfterDays / 60
    val remainingMinutes          = remainingMinutesAfterDays % 60

    (days, hours, remainingMinutes) match {
      case (0, 0, 0) => msgService.getMessage(MsgId.`reminders-at-start`, language)
      case (d, h, m) =>
        val dayLabel =
          if d > 0 then d.toString() + msgService.getMessage(MsgId.`reminders-days-short`, language, d.toString) + " "
          else ""
        val hourLabel =
          if h > 0 then h.toString() + msgService.getMessage(MsgId.`reminders-hours-short`, language, h.toString) + " "
          else ""
        val minuteLabel =
          if m > 0 then
            m.toString() + msgService.getMessage(MsgId.`reminders-minutes-short`, language, m.toString) + " "
          else ""
        msgService.getMessage(MsgId.`reminders-reminder`, language, dayLabel, hourLabel, minuteLabel)
    }

  private def createRemindersKeyboard(
      taskId: Long,
      reminders: List[Reminder],
      pageNumber: Int,
      language: Language
  ) =
    InlineKeyboardMarkup(
      reminders.map { reminder =>
        List(
          inlineKeyboardButton(
            "🔔 " + minutesToLabel(reminder.offsetMinutes, language),
            RemoveReminder(reminder.id, reminder.taskId)
          )
        )
      } ++
        List(
          List(
            inlineKeyboardButton("➕", StandardReminders(taskId, pageNumber)),
            inlineKeyboardButton("🔙", TaskDetails(taskId, pageNumber))
          )
        )
    )

  private def editWithTaskDetails(
      message: Message,
      messageEntities: MessageEntities,
      keyboard: InlineKeyboardMarkup
  ) =
    execDiscardWithHandling(
      editMessageText(
        messageEntities.toPlainText(),
        ChatIntId(message.chat.id).some,
        message.messageId.some,
        entities = messageEntities.toTelegramEntities(),
        replyMarkup = Some(keyboard)
      )
    )

  private def getTaskSafe(id: Long, userId: Long) =
    for
      taskOpt <- taskRepo.findById(id, userId)
      task    <- ZIO.fromOption(taskOpt).orElseFail(new RuntimeException(Errors.NotFound))
    yield task

  private def taskDetails(
      cb: CallbackQuery,
      user: User,
      task: BotTask,
      processTask: BotTask => Task[BotTask],
      generateKeyboard: (User, Long) => Task[InlineKeyboardMarkup]
  ) =
    ackCb(cb) { msg =>
      for
        processedTask <- processTask(task)
        messageEntities = botService.createTaskDetails(processedTask, user.language)
        collaboratorId  = processedTask.getCollaborator(user.id)
        keyboard <- generateKeyboard(user, collaboratorId)
        _        <- editWithTaskDetails(msg, messageEntities, keyboard)
      yield ()
    }

  private def returnTaskDetails(
      cb: CallbackQuery,
      user: User,
      id: Long,
      pageNumber: Int,
      processTask: BotTask => Task[BotTask]
  ) =
    getTaskSafe(id, user.id).flatMap { task =>
      taskDetails(
        cb,
        user,
        task,
        processTask,
        (user, collaboratorId) => kbService.taskDetails(id, pageNumber, user, collaboratorId)
      )
    }
end TaskControllerLive

object TaskControllerLive:
  val layer: URLayer[
    UserRepository & TaskRepository & ReminderRepository & BotService & MessageService & KeyboardService &
      TelegramBotApi,
    TaskController
  ] =
    ZLayer(
      for
        userRepo     <- ZIO.service[UserRepository]
        taskRepo     <- ZIO.service[TaskRepository]
        reminderRepo <- ZIO.service[ReminderRepository]
        botService   <- ZIO.service[BotService]
        msgService   <- ZIO.service[MessageService]
        kbService    <- ZIO.service[KeyboardService]
        api          <- ZIO.service[TelegramBotApi]
      yield TaskControllerLive(userRepo, taskRepo, reminderRepo, botService, msgService, kbService)(using api)
    )
