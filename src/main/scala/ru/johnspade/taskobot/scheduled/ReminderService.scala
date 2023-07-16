package ru.johnspade.taskobot.scheduled

import cats.data.NonEmptyList
import ru.johnspade.taskobot.BotService
import ru.johnspade.taskobot.KeyboardService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.task.Reminder
import ru.johnspade.taskobot.task.ReminderRepository
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository
import telegramium.bots.*
import telegramium.bots.high.Api
import telegramium.bots.high.*
import telegramium.bots.high.implicits.*
import zio.*

import java.time.Instant

trait ReminderService:
  def getEnqueuedReminders(): Task[List[Reminder]]
  def fetchTasks(reminders: List[Reminder]): Task[List[(Reminder, BotTask)]]
  def collectDueReminders(reminders: List[(Reminder, BotTask)]): Task[List[(Reminder, BotTask)]]
  def sendReminder(reminder: Reminder, task: BotTask, user: User): Task[Unit]
  def sendReminders(reminders: List[(Reminder, BotTask)]): Task[List[Reminder]]
  def deleteProcessedReminders(reminders: List[Reminder]): Task[Unit]

object ReminderService:
  def getEnqueuedReminders(): ZIO[ReminderService, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.getEnqueuedReminders())
  def fetchTasks(reminders: List[Reminder]): ZIO[ReminderService, Throwable, List[(Reminder, BotTask)]] =
    ZIO.serviceWithZIO(_.fetchTasks(reminders))
  def collectDueReminders(
      reminders: List[(Reminder, BotTask)]
  ): ZIO[ReminderService, Throwable, List[(Reminder, BotTask)]] = ZIO.serviceWithZIO(_.collectDueReminders(reminders))
  def sendReminder(reminder: Reminder, task: BotTask, user: User): ZIO[ReminderService, Throwable, Unit] =
    ZIO.serviceWithZIO(_.sendReminder(reminder, task, user))
  def sendReminders(reminders: List[(Reminder, BotTask)]): ZIO[ReminderService, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.sendReminders(reminders))
  def deleteProcessedReminders(reminders: List[Reminder]): ZIO[ReminderService, Throwable, Unit] =
    ZIO.serviceWithZIO(_.deleteProcessedReminders(reminders))

final class ReminderServiceLive(
    reminderRepo: ReminderRepository,
    taskRepo: TaskRepository,
    userRepo: UserRepository,
    botService: BotService,
    kbService: KeyboardService
)(using api: Api[Task])
    extends ReminderService:
  override def getEnqueuedReminders(): Task[List[Reminder]] =
    reminderRepo.getEnqueued()

  override def fetchTasks(reminders: List[Reminder]): Task[List[(Reminder, BotTask)]] =
    val taskIds = reminders.map(_.taskId)
    for
      tasksOpt <- ZIO.foreach(NonEmptyList.fromList(taskIds))(taskRepo.findAll(_))
      tasks = tasksOpt.toList.flatten
    yield reminders
      .foldLeft(List.empty[(Reminder, Option[BotTask])]) { case (acc, reminder) =>
        (reminder, tasks.find(_.id == reminder.taskId)) :: acc
      }
      .collect { case (reminder, Some(task)) =>
        reminder -> task
      }

  override def collectDueReminders(reminders: List[(Reminder, BotTask)]): Task[List[(Reminder, BotTask)]] =
    Clock.instant.map { now =>
      reminders.collect {
        case (reminder, task) if isReminderDue(reminder, task, now) =>
          reminder -> task
      }
    }

  override def sendReminder(reminder: Reminder, task: BotTask, user: User): Task[Unit] =
    ZIO
      .foreachDiscard(user.chatId) { chatId =>
        val messageEntities = botService.createTaskDetailsReminder(task, user.language)
        for
          keyboard <- kbService.taskDetails(task.id, 0, user, task.getCollaborator(user.id))
          _ <- Methods
            .sendMessage(
              ChatIntId(chatId),
              messageEntities.toPlainText(),
              entities = messageEntities.toTelegramEntities(),
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
      .unit

  override def sendReminders(reminders: List[(Reminder, BotTask)]): Task[List[Reminder]] =
    for
      remindersToProcess <- processReminders(reminders)
      _                  <- ZIO.foreachDiscard(remindersToProcess)(sendReminder)
    yield remindersToProcess.map((reminder, _, _) => reminder)

  override def deleteProcessedReminders(reminders: List[Reminder]): Task[Unit] =
    ZIO.foreachDiscard(NonEmptyList.fromList(reminders.map(_.id)))(reminderRepo.deleteByIds(_))

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
end ReminderServiceLive

object ReminderServiceLive:
  val layer: ZLayer[
    TaskRepository & UserRepository & BotService & TelegramBotApi & ReminderRepository & KeyboardService,
    Nothing,
    ReminderService
  ] = ZLayer.fromZIO(
    for
      api          <- ZIO.service[TelegramBotApi]
      reminderRepo <- ZIO.service[ReminderRepository]
      taskRepo     <- ZIO.service[TaskRepository]
      userRepo     <- ZIO.service[UserRepository]
      botService   <- ZIO.service[BotService]
      kbService    <- ZIO.service[KeyboardService]
    yield new ReminderServiceLive(reminderRepo, taskRepo, userRepo, botService, kbService)(using api)
  )
