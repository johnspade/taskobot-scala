package ru.johnspade.taskobot.scheduled

import cats.data.NonEmptyList
import ru.johnspade.taskobot.BotService
import ru.johnspade.taskobot.KeyboardService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.task.{BotTask, Reminder, ReminderRepository}
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import telegramium.bots.ChatIntId
import telegramium.bots.high.*
import telegramium.bots.high.implicits.*
import zio.*
import zio.stream.*

import java.time.Instant
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.user.User

trait ReminderNotificationService:
  def scheduleNotifications(): Task[Unit]

object ReminderNotificationService:
  val scheduleNotificationJob: ZIO[ReminderNotificationService, Throwable, Unit] =
    ZIO.serviceWithZIO[ReminderNotificationService](_.scheduleNotifications())

final class ReminderNotificationServiceLive(
    reminderRepo: ReminderRepository,
    taskRepo: TaskRepository,
    userRepo: UserRepository,
    botService: BotService,
    kbService: KeyboardService
)(using api: Api[Task])
    extends ReminderNotificationService:

  def scheduleNotifications(): Task[Unit] =
    ZStream
      .fromSchedule(Schedule.once andThen Schedule.fixed(30.seconds))
      .mapZIO(_ => reminderRepo.getEnqueued())
      .tap(logEnqueuedReminders)
      .filter(_.nonEmpty)
      .mapZIO(fetchTasks)
      .filter(_.nonEmpty)
      .mapZIO(collectDueReminders)
      .tap(logDueReminders)
      .mapZIO(sendReminders)
      .tap(deleteProcessedReminders)
      .tapError(logError)
      .retry(Schedule.exponential(5.seconds))
      .runDrain

  private def logEnqueuedReminders(reminders: List[Reminder]) =
    ZIO.logInfo(s"Enqueued: ${reminders.size}")

  private def fetchTasks(reminders: List[Reminder]): Task[List[(Reminder, Option[BotTask])]] =
    val taskIds = reminders.map(_.taskId)
    for
      tasksOpt <- ZIO.foreach(NonEmptyList.fromList(taskIds))(taskRepo.findAll(_))
      tasks = tasksOpt.toList.flatten
    yield reminders
      .foldLeft(List.empty[(Reminder, Option[BotTask])]) { case (acc, reminder) =>
        (reminder, tasks.find(_.id == reminder.taskId)) :: acc
      }

  private def collectDueReminders(
      reminders: List[(Reminder, Option[BotTask])]
  ): Task[List[(Reminder, BotTask)]] =
    for now <- Clock.instant
    yield reminders.collect {
      case (reminder, Some(task)) if isReminderDue(reminder, task, now) =>
        (reminder, task)
    }

  private def logDueReminders(reminders: List[(Reminder, BotTask)]) =
    ZIO.logInfo(s"Due: ${reminders.size}")

  private def sendReminders(reminders: List[(Reminder, BotTask)]) =
    for
      remindersToProcess <- processReminders(reminders)
      _                  <- ZIO.foreachDiscard(remindersToProcess)(sendReminder)
    yield remindersToProcess.map((reminder, _, _) => reminder)

  private def processReminders(reminders: List[(Reminder, BotTask)]): Task[List[(Reminder, BotTask, User)]] =
    val userIds = reminders.map((reminder, _) => reminder.userId).distinct
    for
      usersOpt <- ZIO.foreach(NonEmptyList.fromList(userIds))(userRepo.findAll(_))
      usersMap = usersOpt.toList.flatten.map(user => user.id -> user).toMap
      withOptionalUsers = reminders.map { case (reminder, task) =>
        (reminder, task, usersMap.get(reminder.userId))
      }

      remindersToProcessOpt <- ZIO.foreach(NonEmptyList.fromList(withOptionalUsers.collect {
        case (reminder, _, Some(_)) => reminder.id
      }))(reminderRepo.fetchAndMarkAsProcessing(_))
      remindersToProcess = remindersToProcessOpt.toList.flatten
      toProcess = withOptionalUsers
        .collect {
          case (reminder, task, Some(user)) if remindersToProcess.exists(_.id == reminder.id) =>
            (reminder, task, user)
        }
        // deduplicate reminders
        .distinctBy((reminder, _, _) => (reminder.taskId, reminder.userId, reminder.offsetMinutes))
    yield toProcess

  private def sendReminder(reminder: Reminder, task: BotTask, user: User) =
    ZIO
      .foreachDiscard(user.chatId) { chatId =>
        val messageEntities = botService.createTaskDetailsReminder(task, user.language)
        for
          keyboard <- kbService.taskDetails(task.id, 0, user)
          _ <- Methods
            .sendMessage(
              ChatIntId(chatId),
              messageEntities.map(_.text).mkString,
              entities = TypedMessageEntity.toMessageEntities(messageEntities),
              replyMarkup = Some(keyboard)
            )
            .exec
            .unit
            .catchSome { case FailedRequest(_, Some(403), Some("Forbidden: bot was blocked by the user")) =>
              ZIO.logWarning(s"Bot was blocked by the user ${user.id.toString}, disabling notifications") *>
                userRepo.setBlockedBotTrue(user.id)
            }
        yield ()
      }
      .when(user.sendNotifications)

  private def isReminderDue(reminder: Reminder, task: BotTask, now: Instant): Boolean =
    task.deadline
      .map { deadline =>
        val reminderTime = deadline
          .minusMinutes(reminder.offsetMinutes.toLong)
          .atZone(task.timezoneOrDefault)
          .toInstant()
        val lateThreshold = reminderTime.plusSeconds(5 * 60) // 5 minutes
        (now.equals(reminderTime) || now.isAfter(reminderTime)) && now.isBefore(lateThreshold)
      }
      .getOrElse(false)

  private def deleteProcessedReminders(reminders: List[Reminder]) =
    ZIO.foreach(NonEmptyList.fromList(reminders.map(_.id)))(reminderRepo.deleteByIds(_))

  private def logError(error: Throwable) =
    ZIO.logError(s"Error in stream: ${error.getMessage}")

object ReminderNotificationServiceLive:
  val layer: ZLayer[
    TelegramBotApi & ReminderRepository & TaskRepository & UserRepository & BotService & KeyboardService,
    Nothing,
    ReminderNotificationService
  ] = ZLayer.fromZIO(
    for
      api          <- ZIO.service[TelegramBotApi]
      reminderRepo <- ZIO.service[ReminderRepository]
      taskRepo     <- ZIO.service[TaskRepository]
      userRepo     <- ZIO.service[UserRepository]
      botService   <- ZIO.service[BotService]
      kbService    <- ZIO.service[KeyboardService]
    yield new ReminderNotificationServiceLive(reminderRepo, taskRepo, userRepo, botService, kbService)(using api)
  )
