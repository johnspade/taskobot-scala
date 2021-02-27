package ru.johnspade.taskobot

import cats.syntax.option._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import ru.johnspade.taskobot.CommandController.CommandController
import ru.johnspade.taskobot.TestEnvironments.PostgresITEnv
import ru.johnspade.taskobot.TestHelpers.{createMessage, mockMessage}
import ru.johnspade.taskobot.TestUsers._
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{ChangeLanguage, Chats, CheckTask}
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskId, TaskText}
import ru.johnspade.taskobot.task.{BotTask, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity._
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.{Api, Methods}
import telegramium.bots.{ChatIntId, Message}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.test.Assertion.{equalTo, isSome}
import zio.test.environment.{TestClock, TestEnvironment}
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{Task, URLayer, ZIO, ZLayer, clock}

object CommandControllerSpec extends DefaultRunnableSpec with MockitoSugar with ArgumentMatchersSugar {
  def spec: ZSpec[TestEnvironment, Throwable] = suite("CommandControllerSpec")(
    suite("/create")(
      testM("should create a task passed as an argument") {
        val taskMessage = createMessage("/create Buy some milk")
        for {
          _ <- TestClock.setTime(Duration.Zero)
          now <- clock.instant
          reply <- ZIO.accessM[CommandController](_.get.onPersonalTaskCommand(taskMessage))
          task <- TaskRepository.findById(TaskId(1L))
          taskAssertions = assert(task)(isSome(equalTo(
            BotTask(TaskId(1L), john.id, TaskText("Buy some milk"), john.id.some, CreatedAt(now.toEpochMilli))
          )))
          replyAssertions = assert(reply)(isSome(equalTo(Methods.sendMessage(
            ChatIntId(johnChatId),
            "Chat: Personal tasks\n1. Buy some milk\n\nSelect the task number to mark it as completed.",
            entities = TypedMessageEntity.toMessageEntities(List(
              plain"Chat: ", bold"Personal tasks", lineBreak,
              plain"1. Buy some milk", lineBreak,
              lineBreak, italic"Select the task number to mark it as completed."
            )),
            replyMarkup = InlineKeyboardMarkups.singleColumn(List(
              inlineKeyboardButton("1", CheckTask(PageNumber(0), TaskId(1L))),
              inlineKeyboardButton("Chat list", Chats(PageNumber(0)))
            )).some
          ))))
        } yield taskAssertions && replyAssertions
      }
    ),

    suite("/settings")(
      testM("should reply with language settings") {
        for {
          reply <- ZIO.accessM[CommandController](_.get.onSettingsCommand(createMessage("/settings")))
        } yield assert(reply)(isSome(equalTo(Methods.sendMessage(
          ChatIntId(johnChatId),
          "Current language: English",
          replyMarkup = InlineKeyboardMarkups.singleButton(inlineKeyboardButton("Switch language", ChangeLanguage)).some
        ))))
      }
    )
  )
    .provideCustomLayerShared(TestEnvironment.env)

  private val botApiMock = mock[Api[Task]]
  when(botApiMock.execute[Message](*)).thenReturn(Task.succeed(mockMessage()))
  when(botApiMock.execute[Either[Boolean, Message]](*)).thenReturn(Task.right(mockMessage()))

  private object TestEnvironment {
    private val botApi = ZLayer.succeed(botApiMock)
    private val userRepo = UserRepository.live
    private val taskRepo = TaskRepository.live
    private val repositories = userRepo ++ taskRepo
    private val botService = repositories >>> BotService.live
    private val commandController = (ZLayer.requires[Clock] ++ botApi ++ botService ++ repositories) >>> CommandController.live

    val env: URLayer[Clock with Blocking, Clock with PostgresITEnv with CommandController with UserRepository with TaskRepository] =
      ZLayer.requires[Clock] ++ TestEnvironments.itLayer >+> commandController ++ repositories
  }
}
