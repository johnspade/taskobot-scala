package ru.johnspade.taskobot.scheduled

import java.time.Instant
import java.time.LocalDateTime

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import org.mockserver.client.MockServerClient

import ru.johnspade.taskobot.BotConfig
import ru.johnspade.taskobot.BotServiceLive
import ru.johnspade.taskobot.CleanupRepository
import ru.johnspade.taskobot.CleanupRepositoryLive
import ru.johnspade.taskobot.KeyboardServiceLive
import ru.johnspade.taskobot.TestBotApi
import ru.johnspade.taskobot.TestBotApi.*
import ru.johnspade.taskobot.TestBotApi.Mocks.*
import ru.johnspade.taskobot.TestDatabase
import ru.johnspade.taskobot.TestUsers.*
import ru.johnspade.taskobot.UTC
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig
import ru.johnspade.taskobot.task.NewTask
import ru.johnspade.taskobot.task.ReminderRepository
import ru.johnspade.taskobot.task.ReminderRepositoryLive
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.task.TaskRepositoryLive
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.UserRepositoryLive

object ReminderServiceSpec extends ZIOSpecDefault:
  private val testEnv =
    ZLayer.make[
      ReminderService & UserRepository & TaskRepository & ReminderRepository & CleanupRepository & MockServerClient
    ](
      TestDatabase.layer,
      TestBotApi.testApiLayer,
      UserRepositoryLive.layer,
      TaskRepositoryLive.layer,
      ReminderRepositoryLive.layer,
      CleanupRepositoryLive.layer,
      MsgConfig.live,
      MessageServiceLive.layer,
      BotServiceLive.layer,
      KeyboardServiceLive.layer,
      ReminderServiceLive.layer,
      BotConfig.live
    )

  private val createTaskAndReminder =
    for {
      now  <- Clock.instant
      task <- createTask("Homework assignment", Some(kaitrin.id))
      _    <- TaskRepository.setDeadline(task.id, Some(LocalDateTime.from(now.atZone(UTC)).plusMinutes(1L)), john.id)
      _    <- ReminderRepository.create(task.id, john.id, offsetMinutes = 0)
    } yield task

  override def spec = (suite("ReminderServiceSpec")(
    test("getEnqueuedReminders returns all enqueued reminders") {
      for {
        _                 <- createTaskAndReminder
        enqueuedReminders <- ReminderService.getEnqueuedReminders()
      } yield assert(enqueuedReminders)(hasSize(equalTo(1)))
    },
    test("fetchTasks returns associated tasks for given reminders") {
      for {
        _                 <- createTaskAndReminder
        enqueuedReminders <- ReminderService.getEnqueuedReminders()
        fetchedTasks      <- ReminderService.fetchTasks(enqueuedReminders)
      } yield assert(fetchedTasks)(hasSize(equalTo(1)))
    },
    test("collectDueReminders filters reminders that are due") {
      for {
        _                 <- createTaskAndReminder
        enqueuedReminders <- ReminderService.getEnqueuedReminders()
        fetchedTasks      <- ReminderService.fetchTasks(enqueuedReminders)
        _                 <- TestClock.adjust(1.minute)
        dueReminders      <- ReminderService.collectDueReminders(fetchedTasks)
      } yield assert(dueReminders)(hasSize(equalTo(1)))
    },
    test("sendReminder sends a reminder to the user") {
      for {
        task              <- createTaskAndReminder
        enqueuedReminders <- ReminderService.getEnqueuedReminders()
        fetchedTasks      <- ReminderService.fetchTasks(enqueuedReminders)
        dueReminders      <- ReminderService.collectDueReminders(fetchedTasks)
        user              <- UserRepository.findById(john.id)
        now               <- Clock.instant
        _                 <- createMock(sendMessageReminder("Homework assignment", task.id, now), messageResponse)
        _ <- ZIO.foreach(dueReminders) { case (reminder, task) =>
          user match {
            case Some(u) =>
              ReminderService.sendReminder(reminder, task, u)
            case None => ZIO.fail("User not found")
          }
        }
      } yield assertTrue(true)
    },
    test("sendReminders sends reminders to users and returns a list of sent reminders") {
      for {
        task              <- createTaskAndReminder
        _                 <- TestClock.adjust(1.minute)
        enqueuedReminders <- ReminderService.getEnqueuedReminders()
        fetchedTasks      <- ReminderService.fetchTasks(enqueuedReminders)
        dueReminders      <- ReminderService.collectDueReminders(fetchedTasks)
        now               <- Clock.instant
        _                 <- createMock(sendMessageReminder("Homework assignment", task.id, now), messageResponse)
        sentReminders     <- ReminderService.sendReminders(dueReminders)
      } yield assert(sentReminders)(hasSize(equalTo(1)))
    },
    test("sendReminder handles the 'Bot was blocked by the user' error") {
      val errorResponse = """
        {
          "ok": false,
          "description": "Forbidden: bot was blocked by the user",
          "error_code": 403
        }
      """
      val text          = "Leave me alone"
      for
        now  <- Clock.instant
        task <- createTask(text, Some(kaitrin.id))
        taskWithDeadline <- TaskRepository
          .setDeadline(task.id, Some(LocalDateTime.from(now.atZone(UTC)).plusMinutes(1L)), john.id)
        reminder    <- ReminderRepository.create(task.id, john.id, offsetMinutes = 0)
        _           <- createMock(sendMessageReminder(text, task.id, now), errorResponse)
        _           <- ReminderService.sendReminder(reminder, taskWithDeadline, john)
        updatedUser <- UserRepository.findById(john.id)
      yield assertTrue(updatedUser.flatMap(_.blockedBot).contains(true))
    },
    test("sendReminder doesn't send reminders if the bot is blocked") {
      for
        task     <- createTask("Please come back", Some(kaitrin.id))
        reminder <- ReminderRepository.create(task.id, john.id, offsetMinutes = 0)
        // No mocks means that the Telegram API wasn't called
        _ <- ReminderService.sendReminder(reminder, task, john.copy(blockedBot = Some(true)))
      yield assertTrue(true)
    },
    test("deleteProcessedReminders deletes reminders that were processed") {
      for {
        task               <- createTaskAndReminder
        _                  <- TestClock.adjust(1.minute)
        enqueuedReminders  <- ReminderService.getEnqueuedReminders()
        fetchedTasks       <- ReminderService.fetchTasks(enqueuedReminders)
        dueReminders       <- ReminderService.collectDueReminders(fetchedTasks)
        now                <- Clock.instant
        _                  <- createMock(sendMessageReminder("Homework assignment", task.id, now), messageResponse)
        sentReminders      <- ReminderService.sendReminders(dueReminders)
        _                  <- ReminderService.deleteProcessedReminders(sentReminders)
        remainingReminders <- ReminderService.getEnqueuedReminders()
      } yield assert(remainingReminders)(isEmpty)
    }
  ) @@ sequential @@
    before {
      for
        _ <- TestClock.setTime(Instant.EPOCH)
        _ <- UserRepository.createOrUpdate(john).orDie
        _ <- UserRepository.createOrUpdate(kaitrin).orDie
      yield ()
    } @@ after(CleanupRepository.truncateTables()))
    .provideShared(testEnv)

  private def createTask(text: String, receiver: Option[Long]) =
    for
      now <- Clock.instant
      task <- TaskRepository.create(
        NewTask(john.id, text, now, UTC, receiver)
      )
    yield task
end ReminderServiceSpec
