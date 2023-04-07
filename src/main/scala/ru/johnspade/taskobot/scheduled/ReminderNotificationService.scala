package ru.johnspade.taskobot.scheduled

import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.task.Reminder
import zio.*
import zio.stream.*

trait ReminderNotificationService:
  def scheduleNotifications(): ZStream[Any, Throwable, List[Reminder]]

object ReminderNotificationService:
  val scheduleNotificationJob: ZIO[ReminderNotificationService, Throwable, ZStream[Any, Throwable, List[Reminder]]] =
    ZIO.serviceWith[ReminderNotificationService](_.scheduleNotifications())

final class ReminderNotificationServiceLive(reminderService: ReminderService) extends ReminderNotificationService:
  override def scheduleNotifications(): ZStream[Any, Throwable, List[Reminder]] =
    ZStream
      .fromSchedule(Schedule.once andThen Schedule.fixed(30.seconds))
      .mapZIO(_ => reminderService.getEnqueuedReminders())
      .filter(_.nonEmpty)
      .tap(logEnqueuedReminders)
      .mapZIO(reminderService.fetchTasks)
      .mapZIO(reminderService.collectDueReminders)
      .filter(_.nonEmpty)
      .tap(logDueReminders)
      .mapZIO(reminderService.sendReminders)
      .tap(logSentReminders)
      .tap(reminderService.deleteProcessedReminders)
      .tapError(logError)

  private def logEnqueuedReminders(reminders: List[Reminder]) =
    ZIO.logInfo(s"Enqueued: ${reminders.size}")

  private def logDueReminders(reminders: List[(Reminder, BotTask)]) =
    ZIO.logInfo(s"Due: ${reminders.size}")

  private def logSentReminders(reminders: List[Reminder]) =
    val info = reminders
      .map { r =>
        s"taskId=${r.taskId} userId=${r.userId} offset=${r.offsetMinutes}"
      }
      .mkString(", ")
    ZIO.logInfo(s"Sent: $info")

  private def logError(error: Throwable) =
    ZIO.logError(s"Error in stream: ${error.getMessage}")

object ReminderNotificationServiceLive:
  val layer: ZLayer[
    ReminderService,
    Nothing,
    ReminderNotificationService
  ] =
    ZLayer.fromZIO(
      ZIO.service[ReminderService].map(reminderService => new ReminderNotificationServiceLive(reminderService))
    )
