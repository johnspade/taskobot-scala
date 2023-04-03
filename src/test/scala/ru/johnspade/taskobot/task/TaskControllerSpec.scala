package ru.johnspade.taskobot.task

import cats.syntax.option.*
import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.BotServiceLive
import ru.johnspade.taskobot.KeyboardServiceLive
import ru.johnspade.taskobot.TestBotApi
import ru.johnspade.taskobot.TestBotApi.Mocks
import ru.johnspade.taskobot.TestBotApi.createMock
import ru.johnspade.taskobot.TestDatabase
import ru.johnspade.taskobot.TestHelpers.callbackQuery
import ru.johnspade.taskobot.TestUsers.*
import ru.johnspade.taskobot.UTC
import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.core.Chats
import ru.johnspade.taskobot.core.CheckTask
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.RemoveTaskDeadline
import ru.johnspade.taskobot.core.TaskDeadlineDate
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.Tasks
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.UserRepositoryLive
import ru.johnspade.tgbot.callbackqueries.CallbackQueryData
import ru.johnspade.tgbot.callbackqueries.ContextCallbackQuery
import telegramium.bots.high.Methods
import telegramium.bots.{User as TgUser}
import zio.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.test.*

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

object TaskControllerSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Throwable] = (suite("TaskControllerSpec")(
    suite("Chats")(
      test("should list chats as a single page") {
        for
          _     <- createMock(Mocks.editMessageTextChatsSinglePage, Mocks.messageResponse)
          _     <- createUsersAndTasks(5)
          reply <- callUserRoute(Chats(firstPage), johnTg)
        yield assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      },
      test("should list chats as multiple pages") {
        for
          _     <- createMock(Mocks.editMessageTextChatsMultiplePages, Mocks.messageResponse)
          _     <- createUsersAndTasks(11)
          reply <- callUserRoute(Chats(1), johnTg)
        yield assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      }
    ),
    suite("Tasks")(
      test("should list tasks as a single page") {
        for
          task  <- createTask("Wash dishes please", kaitrin.id.some)
          _     <- createMock(Mocks.editMessageTextTasksSinglePage(task.id), Mocks.messageResponse)
          reply <- callUserRoute(Tasks(firstPage, kaitrin.id), johnTg)
        yield assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      },
      test("should list tasks as multiple pages") {
        for
          _     <- createMock(Mocks.editMessageTextTasksMultiplePages, Mocks.messageResponse)
          _     <- createKaitrinTasks()
          reply <- callUserRoute(Tasks(1, kaitrin.id), johnTg)
        yield assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      }
    ),
    suite("CheckTask")(
      test("sender should be able to check a task") {
        for
          _           <- createMock(Mocks.editMessageTextTasksKaitrin, Mocks.messageResponse)
          _           <- createMock(Mocks.taskCompletedByJohnMessage, Mocks.messageResponse)
          task        <- createTask("Buy some milk", kaitrin.id.some)
          reply       <- callUserRoute(CheckTask(firstPage, task.id), johnTg)
          checkedTask <- TaskRepository.findById(task.id)
          checkedTaskAssertions = assert(checkedTask.get)(hasField("done", _.done, equalTo(true))) &&
            assert(checkedTask.get)(hasField("doneAt", _.doneAt, isSome))
          replyAssertions = assertTrue(
            reply.contains(Methods.answerCallbackQuery("0", "Task has been marked completed.".some))
          )
        yield replyAssertions && checkedTaskAssertions
      },
      test("receiver should be able to check a task") {
        for
          _           <- createMock(Mocks.editMessageTextCheckTask(0), Mocks.messageResponse)
          _           <- createMock(Mocks.taskCompletedByKaitrinMessage, Mocks.messageResponse)
          task        <- createTask("Buy some milk", kaitrin.id.some)
          reply       <- callUserRoute(CheckTask(firstPage, task.id), kaitrinTg)
          checkedTask <- TaskRepository.findById(task.id)
          checkedTaskAssertions = assert(checkedTask.get)(hasField("done", _.done, equalTo(true))) &&
            assert(checkedTask.get)(hasField("doneAt", _.doneAt, isSome))
          replyAssertions = assertTrue(
            reply.contains(Methods.answerCallbackQuery("0", "Task has been marked completed.".some))
          )
        yield replyAssertions && checkedTaskAssertions
      },
      test("cannot check someone else's task") {
        for
          task          <- createTask("Try Taskobot", kaitrin.id.some)
          reply         <- callUserRoute(CheckTask(firstPage, task.id), TgUser(0, isBot = false, "Bob"))
          uncheckedTask <- TaskRepository.findById(task.id)
          uncheckedTaskAssertions = assert(uncheckedTask.get)(hasField("done", _.done, equalTo(false))) &&
            assert(uncheckedTask.get)(hasField("doneAt", _.doneAt, isNone))
          replyAssertions = assertTrue(reply.contains(Methods.answerCallbackQuery("0", "Not found.".some)))
        yield uncheckedTaskAssertions && replyAssertions
      }
    ),
    suite("ConfirmTask")(
      test("receiver should be able to confirm task") {
        for
          _             <- createMock(Mocks.removeReplyMarkup, Mocks.messageResponse)
          task          <- createTask("Buy some milk")
          reply         <- confirmTask(ConfirmTask(task.id.some, john.id.some), kaitrinTg)
          confirmedTask <- TaskRepository.findById(task.id)
          confirmedTaskAssertions    = assertTrue(confirmedTask.get.receiver.contains(kaitrin.id))
          confirmTaskReplyAssertions = assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
        yield confirmedTaskAssertions && confirmTaskReplyAssertions
      },
      test("sender should not be able to confirm task") {
        for
          task            <- createTask("Buy groceries")
          reply           <- confirmTask(ConfirmTask(task.id.some, john.id.some), johnTg)
          unconfirmedTask <- TaskRepository.findById(task.id)
          confirmTaskReplyAssertions = assertTrue(
            reply.contains(Methods.answerCallbackQuery("0", "The task must be confirmed by the receiver".some))
          )
          unconfirmedTaskAssertions = assert(unconfirmedTask.get.receiver)(isNone)
        yield confirmTaskReplyAssertions && unconfirmedTaskAssertions
      },
      test("cannot confirm task with wrong senderId") {
        for
          task <- createTask("Buy some bread")
          bobId = 0L
          reply           <- confirmTask(ConfirmTask(task.id.some, bobId.some), TgUser(bobId, isBot = false, "Bob"))
          unconfirmedTask <- TaskRepository.findById(task.id)
          confirmTaskReplyAssertions = assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
          unconfirmedTaskAssertions  = assert(unconfirmedTask.get.receiver)(isNone)
        yield confirmTaskReplyAssertions && unconfirmedTaskAssertions
      }
    ),
    suite("TaskDetails")(
      test("should handle TaskDetails callback query") {
        for
          _     <- TestClock.setTime(Instant.EPOCH)
          now   <- Clock.instant
          task  <- createTask("Buy some milk", kaitrin.id.some)
          _     <- createMock(Mocks.editMessageTextTaskDetails(task.id, now), Mocks.messageResponse)
          reply <- callUserRoute(TaskDetails(task.id, 0), johnTg)
        yield assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      }
    ),
    suite("TaskDeadlineDate")(
      test("should update task deadline date") {
        for
          _    <- TestClock.setTime(Instant.EPOCH)
          now  <- Clock.instant
          task <- createTask("Buy some milk", kaitrin.id.some)
          _    <- createMock(Mocks.editMessageTextTaskDeadlineUpdated(task.id, now), Mocks.messageResponse)
          deadlineDate = LocalDate.ofInstant(now, UTC)
          reply       <- callUserRoute(TaskDeadlineDate(task.id, deadlineDate), johnTg)
          updatedTask <- TaskRepository.findById(task.id)
        yield assert(updatedTask.get.deadline)(equalTo(deadlineDate.atStartOfDay().some)) &&
          assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      }
    ),
    suite("RemoveTaskDeadline")(
      test("should remove task deadline") {
        for
          _    <- TestClock.setTime(Instant.EPOCH)
          now  <- Clock.instant
          task <- createTask("Buy some milk", kaitrin.id.some)
          deadlineDate = LocalDateTime.ofInstant(now, UTC)
          taskWithDeadline <- TaskRepository.setDeadline(task.id, deadlineDate.some, kaitrin.id)
          _                <- createMock(Mocks.editMessageTextTaskDeadlineRemoved(task.id, now), Mocks.messageResponse)
          reply            <- callUserRoute(RemoveTaskDeadline(task.id), johnTg)
          updatedTask      <- TaskRepository.findById(task.id)
        yield assert(taskWithDeadline.deadline)(isSome) && assert(updatedTask.get.deadline)(isNone) &&
          assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      }
    ),
    suite("TimePicker")(
      test("should handle TimePicker callback query") {
        for
          _    <- TestClock.setTime(Instant.EPOCH)
          now  <- Clock.instant
          task <- createTask("Buy some milk", kaitrin.id.some)
          deadlineDate = LocalDateTime.ofInstant(now, UTC)
          _     <- TaskRepository.setDeadline(task.id, deadlineDate.some, kaitrin.id)
          _     <- createMock(Mocks.editMessageTextTimePicker(task.id, now), Mocks.messageResponse)
          reply <- callUserRoute(TimePicker(task.id, 13.some, 15.some, confirm = true), johnTg)
        yield assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
      }
    )
  )
    @@ sequential
    @@ before {
      for
        _ <- UserRepository.createOrUpdate(john).orDie
        _ <- UserRepository.createOrUpdate(kaitrin).orDie
      yield ()
    }
    @@ TestAspect.after {
      for
        _ <- TaskRepository.clear()
        _ <- UserRepository.clear()
      yield ()
    })
    .provideShared(env)

  private val firstPage = 0

  private def createTask(text: String, receiver: Option[Long] = None) =
    for
      now <- Clock.instant
      task <- TaskRepository.create(
        NewTask(john.id, text, now, receiver, timezone = UTC)
      )
    yield task

  private def createUsersAndTasks(count: Int) =
    for
      now <- Clock.instant
      usersAndTasks = List.tabulate(count) { n =>
        val user = User(n.toLong, n.toString, Language.English)
        user -> NewTask(john.id, n.toString, now, user.id.some, timezone = UTC)
      }
      _ <- ZIO.foreachDiscard(usersAndTasks) { case (user, task) =>
        UserRepository.createOrUpdate(user) *> TaskRepository.create(task)
      }
    yield ()

  private def createKaitrinTasks() =
    for
      now <- Clock.instant
      tasks = List.tabulate(11) { n =>
        NewTask(john.id, n.toString, now, kaitrin.id.some, timezone = UTC)
      }
      _ <- ZIO.foreachDiscard(tasks)(TaskRepository.create)
    yield ()

  private def confirmTask(cbData: ConfirmTask, from: TgUser) =
    ZIO.serviceWithZIO[TaskController] {
      _.routes
        .run(CallbackQueryData(cbData, callbackQuery(cbData, from, inlineMessageId = "0".some)))
        .value
        .map(_.flatten)
    }

  private def callUserRoute(cbData: CbData, from: TgUser) =
    ZIO.serviceWithZIO[TaskController] {
      _.userRoutes
        .run(
          ContextCallbackQuery(
            toUser(from),
            CallbackQueryData(cbData, callbackQuery(cbData, from, inlineMessageId = "0".some))
          )
        )
        .value
        .map(_.flatten)
    }

  private val env = ZLayer.make[MockServerClient with TaskController with UserRepository with TaskRepository](
    TestDatabase.layer,
    TestBotApi.testApiLayer,
    UserRepositoryLive.layer,
    TaskRepositoryLive.layer,
    ReminderRepositoryLive.layer,
    MsgConfig.live,
    MessageServiceLive.layer,
    KeyboardServiceLive.layer,
    BotServiceLive.layer,
    TaskControllerLive.layer
  )
