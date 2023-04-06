package ru.johnspade.taskobot.scheduled

import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.BotServiceLive
import ru.johnspade.taskobot.CleanupRepository
import ru.johnspade.taskobot.CleanupRepositoryLive
import ru.johnspade.taskobot.KeyboardServiceLive
import ru.johnspade.taskobot.TestBotApi
import ru.johnspade.taskobot.TestBotApi.Mocks.*
import ru.johnspade.taskobot.TestBotApi.Mocks.sendMessageReminder
import ru.johnspade.taskobot.TestBotApi.*
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
import zio.*
import zio.test.TestAspect.*
import zio.test.*

import java.time.LocalDateTime
import java.time.Instant

object ReminderNotificationServiceSpec extends ZIOSpecDefault:
  private val testEnv =
    ZLayer.make[
      ReminderNotificationService & UserRepository & TaskRepository & ReminderRepository & CleanupRepository &
        MockServerClient
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
      ReminderNotificationServiceLive.layer,
      ReminderServiceLive.layer
    )

  def spec = (suite("ReminderNotificationServiceSpec")(
    test("schedules and sends reminders correctly") {
      for
        _    <- TestClock.setTime(Instant.EPOCH)
        now  <- Clock.instant
        task <- createTask("Homework assignment", Some(kaitrin.id))
        _    <- TaskRepository.setDeadline(task.id, Some(LocalDateTime.from(now.atZone(UTC)).plusMinutes(1L)), john.id)
        _    <- ReminderRepository.create(task.id, john.id, offsetMinutes = 0)
        _    <- createMock(sendMessageReminder("Homework assignment", task.id, now), messageResponse)
        stream <- ReminderNotificationService.scheduleNotificationJob
        fiber  <- stream.takeUntilZIO(_ => remindersTableEmpty).runDrain.fork
        _      <- TestClock.adjust(1.minute)
        _      <- fiber.join
      yield assertTrue(true)
    },
    test("sends reminders at correct intervals") {
      for
        _     <- TestClock.setTime(Instant.EPOCH)
        now   <- Clock.instant
        task1 <- createTask("Homework assignment1", Some(kaitrin.id))
        _ <- TaskRepository.setDeadline(task1.id, Some(LocalDateTime.from(now.atZone(UTC)).plusMinutes(1L)), john.id)
        _ <- ReminderRepository.create(task1.id, john.id, offsetMinutes = 0)
        task2 <- createTask("Homework assignment2", Some(kaitrin.id))
        _ <- TaskRepository.setDeadline(task2.id, Some(LocalDateTime.from(now.atZone(UTC)).plusMinutes(2L)), john.id)
        _ <- ReminderRepository.create(task2.id, john.id, offsetMinutes = 0)
        _ <- createMock(sendMessageReminder("Homework assignment1", task1.id, now), messageResponse)
        stream <- ReminderNotificationService.scheduleNotificationJob
        fiber  <- stream.takeUntilZIO(_ => remindersTableEmpty).runDrain.fork
        _      <- TestClock.adjust(70.seconds)
        _ <- createMock(
          sendMessageReminder("Homework assignment2", task2.id, now, "1970-01-01 00:02"),
          messageResponse
        )
        _ <- TestClock.adjust(2.minutes)
        _ <- fiber.join
      yield assertTrue(true)
    }
  ) @@ sequential
    @@ before {
      for
        _ <- UserRepository.createOrUpdate(john).orDie
        _ <- UserRepository.createOrUpdate(kaitrin).orDie
      yield ()
    } @@ TestAspect.after {
      for
        _ <- CleanupRepository.clearReminders()
        _ <- CleanupRepository.clearTasks()
        _ <- CleanupRepository.clearUsers()
      yield ()
    }).provideShared(testEnv)

  private val remindersTableEmpty = ReminderRepository.getEnqueued().map(_.isEmpty)

  private def createTask(text: String, receiver: Option[Long]) =
    for
      now <- Clock.instant
      task <- TaskRepository.create(
        NewTask(john.id, text, now, UTC, receiver)
      )
    yield task
