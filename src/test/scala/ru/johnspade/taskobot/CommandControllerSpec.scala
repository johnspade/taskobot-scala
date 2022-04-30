package ru.johnspade.taskobot

import cats.syntax.option.*
import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.CommandController
import ru.johnspade.taskobot.TestBotApi.{Mocks, createMock}
import ru.johnspade.taskobot.TestHelpers.createMessage
import ru.johnspade.taskobot.TestUsers.*
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{ChangeLanguage, Chats, CheckTask}
import ru.johnspade.taskobot.messages.{MessageServiceLive, MsgConfig}
import ru.johnspade.taskobot.task.{BotTask, TaskRepository, TaskRepositoryLive}
import ru.johnspade.taskobot.user.{UserRepository, UserRepositoryLive}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.Methods
import telegramium.bots.ChatIntId
import zio.Duration
import zio.test.*
import zio.*

object CommandControllerSpec extends ZIOSpecDefault:
  def spec: ZSpec[TestEnvironment with Scope, Any] = suite("CommandControllerSpec")(
    suite("/create")(
      test("should create a task passed as an argument") {
        val taskMessage = createMessage("/create Buy some milk")
        for
          _ <- createMock(
            Mocks.taskCreatedMessage("Personal task \"Buy some milk\" has been created."),
            Mocks.messageResponse
          )
          _     <- TestClock.setTime(Duration.Zero)
          now   <- Clock.instant
          reply <- ZIO.serviceWithZIO[CommandController](_.onPersonalTaskCommand(taskMessage))
          task  <- TaskRepository.findById(1L)
          taskAssertions = assertTrue(
            task.contains(BotTask(1L, john.id, "Buy some milk", john.id.some, now.toEpochMilli))
          )
          replyAssertions = assertTrue(
            reply.contains(
              Methods.sendMessage(
                ChatIntId(johnChatId),
                "Chat: Personal tasks\n1. Buy some milk\n\nSelect the task number to mark it as completed.",
                entities = TypedMessageEntity.toMessageEntities(
                  List(
                    plain"Chat: ",
                    bold"Personal tasks",
                    lineBreak,
                    plain"1. Buy some milk",
                    italic"",
                    lineBreak,
                    lineBreak,
                    italic"Select the task number to mark it as completed."
                  )
                ),
                replyMarkup = InlineKeyboardMarkups
                  .singleColumn(
                    List(
                      inlineKeyboardButton("1", CheckTask(0, 1L)),
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
      test("should reply with language settings") {
        ZIO
          .serviceWithZIO[CommandController](_.onSettingsCommand(createMessage("/settings")))
          .map(reply =>
            assertTrue(
              reply.contains(
                Methods.sendMessage(
                  ChatIntId(johnChatId),
                  "Current language: English",
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
