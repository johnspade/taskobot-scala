package ru.johnspade.taskobot

import cats.syntax.option.*
import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.TestBotApi.Mocks
import ru.johnspade.taskobot.TestBotApi.createMock
import ru.johnspade.taskobot.TestHelpers.createMessage
import ru.johnspade.taskobot.TestUsers.*
import ru.johnspade.taskobot.core.ChangeLanguage
import ru.johnspade.taskobot.core.Chats
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.task.TaskRepositoryLive
import ru.johnspade.taskobot.user.UserRepositoryLive
import telegramium.bots.ChatIntId
import telegramium.bots.high.Methods
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.messageentities.MessageEntities
import zio.*
import zio.test.*

import java.time.Instant

object CommandControllerSpec extends ZIOSpecDefault:
  def spec: Spec[TestEnvironment with Scope, Any] = suite("CommandControllerSpec")(
    suite("/create")(
      test("should create a task passed as an argument") {
        val taskMessage = createMessage("/create Buy some milk")
        for
          _ <- createMock(
            Mocks.taskCreatedMessage("Personal task \"Buy some milk\" has been created."),
            Mocks.messageResponse
          )
          _     <- TestClock.setTime(Instant.EPOCH)
          now   <- Clock.instant
          reply <- ZIO.serviceWithZIO[CommandController](_.onPersonalTaskCommand(taskMessage))
          task  <- TaskRepository.findById(1L)
          expectedTask   = BotTask(1L, john.id, "Buy some milk", john.id.some, now, timezone = Some(UTC))
          taskAssertions = assertTrue(task.contains(expectedTask))
          replyAssertions = assertTrue(
            reply.contains(
              Methods.sendMessage(
                ChatIntId(johnChatId),
                "Chat: Personal tasks\n1. Buy some milk\n",
                entities = MessageEntities()
                  .plain("Chat: ")
                  .bold("Personal tasks")
                  .br()
                  .plain("1. Buy some milk")
                  .italic("")
                  .toTelegramEntities(),
                replyMarkup = InlineKeyboardMarkups
                  .singleColumn(
                    List(
                      inlineKeyboardButton("1", TaskDetails(1L, 0)),
                      inlineKeyboardButton("Chat list", Chats(0))
                    )
                  )
                  .some
              )
            )
          )
        yield taskAssertions && replyAssertions
      }
    ),
    suite("/settings")(
      test("should reply with language and timezone settings") {
        ZIO
          .serviceWithZIO[CommandController](_.onSettingsCommand(createMessage("/settings")))
          .map(reply =>
            assertTrue(
              reply.contains(
                Methods.sendMessage(
                  ChatIntId(johnChatId),
                  """
                    |Current language: English
                    |Timezone: UTC
                    |""".stripMargin,
                  replyMarkup =
                    InlineKeyboardMarkups.singleButton(inlineKeyboardButton("Switch language", ChangeLanguage)).some
                )
              )
            )
          )
      }
    )
  )
    .provideCustomShared(env)

  private val env =
    ZLayer.make[MockServerClient with CommandController with TaskRepository](
      TestDatabase.layer,
      TestBotApi.testApiLayer,
      UserRepositoryLive.layer,
      TaskRepositoryLive.layer,
      MsgConfig.live,
      MessageServiceLive.layer,
      KeyboardServiceLive.layer,
      BotServiceLive.layer,
      CommandControllerLive.layer
    )
