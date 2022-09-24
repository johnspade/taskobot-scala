package ru.johnspade.taskobot.task

import cats.syntax.option.*
import com.dimafeng.testcontainers.MockServerContainer
import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.TestHelpers.{callbackQuery, mockMessage}
import ru.johnspade.taskobot.TestUsers.{john, johnChatId, johnTg, kaitrin, kaitrinChatId, kaitrinTg}
import ru.johnspade.taskobot.core.TelegramOps.{inlineKeyboardButton, toUser}
import ru.johnspade.taskobot.core.{CbData, Chats, CheckTask, ConfirmTask, Tasks}
import ru.johnspade.taskobot.messages.{Language, MessageServiceLive, MsgConfig}
import ru.johnspade.taskobot.task.TaskController
import ru.johnspade.taskobot.user.{User, UserRepository, UserRepositoryLive}
import ru.johnspade.taskobot.{BotService, BotServiceLive, KeyboardServiceLive, TestBotApi, TestDatabase}
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryData, ContextCallbackQuery}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import ru.johnspade.taskobot.TestBotApi.{Mocks, createMock}
import telegramium.bots.client.Method
import telegramium.bots.high.keyboards.{InlineKeyboardButtons, InlineKeyboardMarkups}
import telegramium.bots.high.{Api, Methods}
import telegramium.bots.{ChatIntId, InlineKeyboardMarkup, Message, User as TgUser}
import zio.test.Assertion.{equalTo, hasField, isNone, isSome}
import zio.test.TestAspect.{before, sequential}
import zio.test.*
import zio.*

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
            reply.contains(Methods.answerCallbackQuery("0", "Task has been marked as completed.".some))
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
            reply.contains(Methods.answerCallbackQuery("0", "Task has been marked as completed.".some))
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
        NewTask(john.id, text, now.toEpochMilli, receiver)
      )
    yield task

  private def createUsersAndTasks(count: Int) =
    for
      now <- Clock.instant
      usersAndTasks = List.tabulate(count) { n =>
        val user = User(n.toLong, n.toString, Language.English)
        user -> NewTask(john.id, n.toString, now.toEpochMilli, user.id.some)
      }
      _ <- ZIO.foreachDiscard(usersAndTasks) { case (user, task) =>
        UserRepository.createOrUpdate(user) *> TaskRepository.create(task)
      }
    yield ()

  private def createKaitrinTasks() =
    for
      now <- Clock.instant
      tasks = List.tabulate(11) { n =>
        NewTask(john.id, n.toString, now.toEpochMilli, kaitrin.id.some)
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
    MsgConfig.live,
    MessageServiceLive.layer,
    KeyboardServiceLive.layer,
    BotServiceLive.layer,
    TaskControllerLive.layer
  )
