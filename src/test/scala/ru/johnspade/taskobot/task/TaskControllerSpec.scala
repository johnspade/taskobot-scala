package ru.johnspade.taskobot.task

import cats.syntax.option._
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import ru.johnspade.taskobot.MigrationAspects.migrate
import ru.johnspade.taskobot.TestAssertions.isMethodsEqual
import ru.johnspade.taskobot.TestEnvironments.PostgresITEnv
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryData
import ru.johnspade.taskobot.core.{CbData, ConfirmTask}
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.task.TaskController.TaskController
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskText}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags.{FirstName, UserId}
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{BotService, TestEnvironments}
import telegramium.bots.client.Method
import telegramium.bots.high.{Api, Methods}
import telegramium.bots.{CallbackQuery, Chat, Message, User => TgUser}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.{before, sequential}
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Task, URLayer, ZIO, ZLayer, clock}

object TaskControllerSpec extends DefaultRunnableSpec with MockitoSugar with ArgumentMatchersSugar {
  override def spec: ZSpec[TestEnvironment, Throwable] = (suite("TaskControllerSpec")(
    suite("ConfirmTask")(
      testM("receiver should be able to confirm task") {
        for {
          now <- clock.instant
          task <- TaskRepository.create(
            NewTask(UserId(johnId), TaskText("Buy some milk"), CreatedAt(now.toEpochMilli))
          )
          cbData = ConfirmTask(task.id.some)
          reply <- ZIO.accessM[TaskController] {
            _.get.routes.run(CallbackQueryData(cbData, callbackQuery(cbData, kaitrin, inlineMessageId = "0".some))).value.map(_.flatten)
          }
          confirmedTask <- TaskRepository.findById(task.id)
          confirmedTaskAssertions = assert(confirmedTask.get.receiver)(isSome(equalTo(UserId(kaitrinId))))
          _ <- ZIO.effect(Thread.sleep(1000))
          removeMarkupAssertions = verifyMethodCall(botApiMock, Methods.editMessageReplyMarkup(inlineMessageId = "0".some, replyMarkup = None))
          confirmTaskReplyAssertions = assert(reply)(isSome(equalTo(Methods.answerCallbackQuery("0"))))
        } yield confirmedTaskAssertions && removeMarkupAssertions && confirmTaskReplyAssertions
      },

      testM("sender should not be able to confirm task") {
        for {
          now <- clock.instant
          task <- TaskRepository.create(
            NewTask(UserId(johnId), TaskText("Buy groceries"), CreatedAt(now.toEpochMilli))
          )
          cbData = ConfirmTask(task.id.some)
          reply <- ZIO.accessM[TaskController] {
            _.get.routes.run(CallbackQueryData(cbData, callbackQuery(cbData, john, inlineMessageId = "0".some))).value.map(_.flatten)
          }
          unconfirmedTask <- TaskRepository.findById(task.id)
          confirmTaskReplyAssertions = assert(reply)(isSome(equalTo(
            Methods.answerCallbackQuery("0", "The task must be confirmed by the receiver".some)
          )))
          unconfirmedTaskAssertions = assert(unconfirmedTask.get.receiver)(isNone)
        } yield confirmTaskReplyAssertions && unconfirmedTaskAssertions
      }
    )
  )
    @@ sequential
    @@ before(UserRepository.createOrUpdate(User(UserId(johnId), FirstName(john.firstName), Language.English)))
    @@ migrate)
    .provideCustomLayerShared(TestEnvironment.env)

  private val botApiMock = mock[Api[Task]]
  when(botApiMock.execute[Message](*)).thenReturn(Task.succeed(mockMessage()))
  when(botApiMock.execute[Either[Boolean, Message]](*)).thenReturn(Task.right(mockMessage()))

  private val johnId = 1337
  private val john = TgUser(johnId, isBot = false, "John")
  private val kaitrinId = 911
  private val kaitrin = TgUser(kaitrinId, isBot = false, "Kaitrin")

  private def mockMessage(chatId: Int = 0) =
    Message(0, date = 0, chat = Chat(chatId, `type` = ""), from = TgUser(id = 123, isBot = true, "Taskobot").some)

  private def callbackQuery(data: CbData, from: TgUser, inlineMessageId: Option[String] = None) =
    CallbackQuery("0", from, chatInstance = "", data = data.toCsv.some, message = mockMessage().some, inlineMessageId = inlineMessageId)

  private def verifyMethodCall[Res](apiMock: Api[Task], method: Method[Res]) = {
    val captor = ArgCaptor[Method[Res]]
    verify(apiMock, atLeastOnce).execute(captor)
    assert(captor.values)(Assertion.exists(isMethodsEqual(method)))
  }

  private object TestEnvironment {
    private val botApi = ZLayer.succeed(botApiMock)
    private val userRepo = UserRepository.live
    private val taskRepo = TaskRepository.live
    private val repositories = userRepo ++ taskRepo
    private val botService = repositories >>> BotService.live
    private val taskController = (ZLayer.requires[Clock] ++ botApi ++ botService ++ repositories) >>> TaskController.live

    val env: URLayer[Clock with Blocking, PostgresITEnv with TaskController with TaskRepository with UserRepository] =
      ZLayer.requires[Clock] ++ TestEnvironments.itLayer >+> taskController ++ repositories
  }
}
