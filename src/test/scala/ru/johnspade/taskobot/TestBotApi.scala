package ru.johnspade.taskobot

import com.dimafeng.testcontainers.MockServerContainer
import zio.*
import org.http4s.blaze.client.BlazeClientBuilder
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.{HttpRequest, JsonBody}
import zio.interop.catz.*
import telegramium.bots.high.{Api, BotApi}
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.TestUsers.{johnChatId, kaitrin, kaitrinChatId}
import ru.johnspade.taskobot.core.{Chats, CheckTask, ConfirmTask, Ignore, SetLanguage, Tasks}
import ru.johnspade.taskobot.messages.Language
import cats.syntax.option.*
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import telegramium.bots.{ChatIntId, InlineKeyboardMarkup, Message, ReplyKeyboardMarkup}
import telegramium.bots.client.Method
import telegramium.bots.high.keyboards.*
import telegramium.bots.high.Methods

object TestBotApi:
  private val mockServerContainer: ULayer[MockServerContainer] =
    ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.attemptBlocking {
          val container = MockServerContainer("5.13.2")
          container.start()
          container
        }.orDie
      }(container => ZIO.attemptBlocking(container.stop()).orDie)
    }

  private val mockServerClient: URLayer[MockServerContainer, MockServerClient] =
    ZLayer(
      ZIO
        .service[MockServerContainer]
        .flatMap(mockServer => ZIO.attemptBlocking(new MockServerClient("localhost", mockServer.serverPort)).orDie)
    )

  private val api: URLayer[MockServerContainer, TelegramBotApi] = ZLayer.scoped {
    (for
      mockServer <- ZIO.service[MockServerContainer]
      httpClient <- BlazeClientBuilder[Task].resource.toScopedZIO
      botApi = BotApi[Task](httpClient, mockServer.endpoint)
    yield botApi).orDie
  }

  val testApiLayer: ULayer[MockServerContainer with MockServerClient with TelegramBotApi] =
    ZLayer.make[MockServerContainer with MockServerClient with TelegramBotApi](
      mockServerContainer,
      mockServerClient,
      api
    )

  def createMock[Res](method: Method[Res], responseBody: String): RIO[MockServerClient, Unit] =
    ZIO
      .service[MockServerClient]
      .flatMap { client =>
        ZIO.attemptBlocking(
          client
            .when(
              request("/" + method.payload.name)
                .withMethod("POST")
                .withBody(new JsonBody(method.payload.json.toString))
            )
            .respond(response().withBody(responseBody))
        )
      }

  object Mocks:
    val messageResponse: String =
      """
        {
          "ok": true,
          "result": {
            "message_id": 0,
            "date": 1593365356,
            "chat": {
            "id": 0,
            "type": "private"
          },
            "text": "Lorem ipsum"
          }
        }
      """

    val listLanguages: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        "Current language: English",
        ChatIntId(johnChatId).some,
        messageId = 0.some,
        replyMarkup = InlineKeyboardMarkups
          .singleColumn(
            List(
              inlineKeyboardButton("English", Ignore),
              inlineKeyboardButton("Русский", SetLanguage(Language.Russian)),
              inlineKeyboardButton("Turkish", SetLanguage(Language.Turkish)),
              inlineKeyboardButton("Italian", SetLanguage(Language.Italian)),
              inlineKeyboardButton("Traditional Chinese", SetLanguage(Language.TraditionalChinese)),
              inlineKeyboardButton("Spanish", SetLanguage(Language.Spanish))
            )
          )
          .some
      )

    val listLanguagesRussian: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        "Текущий язык: Русский",
        ChatIntId(johnChatId).some,
        messageId = 0.some,
        replyMarkup = InlineKeyboardMarkups
          .singleColumn(
            List(
              inlineKeyboardButton("English", SetLanguage(Language.English)),
              inlineKeyboardButton("Русский", Ignore),
              inlineKeyboardButton("Turkish", SetLanguage(Language.Turkish)),
              inlineKeyboardButton("Italian", SetLanguage(Language.Italian)),
              inlineKeyboardButton("Traditional Chinese", SetLanguage(Language.TraditionalChinese)),
              inlineKeyboardButton("Spanish", SetLanguage(Language.Spanish))
            )
          )
          .some
      )

    val languageChangedMessage: Method[Message] =
      Methods.sendMessage(
        ChatIntId(johnChatId),
        text = "Язык изменен",
        replyMarkup = ReplyKeyboardMarkup(
          List(
            List(KeyboardButtons.text("\uD83D\uDCCB Задачи"), KeyboardButtons.text("➕ Новая личная задача")),
            List(KeyboardButtons.text("\uD83D\uDE80 Новая совместная задача")),
            List(KeyboardButtons.text("⚙️ Настройки"), KeyboardButtons.text("❓ Справка"))
          ),
          resizeKeyboard = true.some
        ).some
      )

    def taskCreatedMessage(text: String): Method[Message] =
      Methods.sendMessage(
        ChatIntId(johnChatId),
        text,
        replyMarkup = ReplyKeyboardMarkup(
          List(
            List(KeyboardButtons.text("\uD83D\uDCCB Tasks"), KeyboardButtons.text("➕ New personal task")),
            List(KeyboardButtons.text("\uD83D\uDE80 New collaborative task")),
            List(KeyboardButtons.text("⚙️ Settings"), KeyboardButtons.text("❓ Help"))
          ),
          resizeKeyboard = true.some
        ).some
      )

    val removeReplyMarkup: Method[Either[Boolean, Message]] =
      Methods.editMessageReplyMarkup(inlineMessageId = "0".some)

    val addConfirmButton: Method[Either[Boolean, Message]] =
      Methods.editMessageReplyMarkup(
        inlineMessageId = "0".some,
        replyMarkup = InlineKeyboardMarkups
          .singleButton(
            inlineKeyboardButton("Confirm task", ConfirmTask(1L.some, 1337L.some))
          )
          .some
      )

    val editMessageTextList: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        "Chat: John\n1. Buy some milk – John\n\nSelect the task number to mark it as completed.",
        ChatIntId(kaitrinChatId).some,
        messageId = 0.some,
        entities = TypedMessageEntity.toMessageEntities(
          List(
            plain"Chat: ",
            bold"John",
            lineBreak,
            plain"1. Buy some milk",
            italic" – John",
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

    def editMessageTextCheckTask(chatId: Int): Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        "Chat: John\n\nSelect the task number to mark it as completed.",
        ChatIntId(chatId).some,
        messageId = 0.some,
        entities = TypedMessageEntity.toMessageEntities(
          List(
            plain"Chat: ",
            bold"John",
            lineBreak,
            lineBreak,
            italic"Select the task number to mark it as completed."
          )
        ),
        replyMarkup = InlineKeyboardMarkups.singleButton(inlineKeyboardButton("Chat list", Chats(0))).some
      )

    val taskCompletedMessage: Method[Message] =
      Methods.sendMessage(ChatIntId(kaitrinChatId), "Task \"Buy some milk\" has been marked as completed by Kaitrin.")

    val editMessageTextPersonalTasks: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        "Chat: Personal tasks\n\nSelect the task number to mark it as completed.",
        ChatIntId(johnChatId).some,
        messageId = 0.some,
        entities = TypedMessageEntity.toMessageEntities(
          List(
            plain"Chat: ",
            bold"Personal tasks",
            lineBreak,
            lineBreak,
            italic"Select the task number to mark it as completed."
          )
        ),
        replyMarkup = InlineKeyboardMarkups.singleButton(inlineKeyboardButton("Chat list", Chats(0))).some
      )

    val editMessageTextChatsSinglePage: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        chatId = ChatIntId(0).some,
        messageId = 0.some,
        text = "Chats with tasks",
        replyMarkup = InlineKeyboardMarkups
          .singleColumn(
            List.tabulate(5)(n => inlineKeyboardButton(n.toString, Tasks(0, n.toLong))) ++
              List(InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade"))
          )
          .some
      )

    val editMessageTextChatsMultiplePages: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        chatId = ChatIntId(0).some,
        messageId = 0.some,
        text = "Chats with tasks",
        replyMarkup = InlineKeyboardMarkups
          .singleColumn(
            List.tabulate(5) { n =>
              val id = n + 5
              inlineKeyboardButton(id.toString, Tasks(0, id.toLong))
            } ++ List(
              inlineKeyboardButton("Previous page", Chats(0)),
              inlineKeyboardButton("Next page", Chats(2))
            ) ++
              List(InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade"))
          )
          .some
      )

    def editMessageTextTasksSinglePage(taskId: Long): Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        chatId = ChatIntId(0).some,
        messageId = 0.some,
        text = "Chat: Kaitrin\n1. Wash dishes please – John\n\nSelect the task number to mark it as completed.",
        entities = TypedMessageEntity.toMessageEntities(
          List(
            plain"Chat: ",
            bold"Kaitrin",
            lineBreak,
            plain"1. Wash dishes please",
            italic" – John",
            lineBreak,
            lineBreak,
            italic"Select the task number to mark it as completed."
          )
        ),
        replyMarkup = InlineKeyboardMarkups
          .singleColumn(
            List(
              inlineKeyboardButton("1", CheckTask(0, taskId)),
              inlineKeyboardButton("Chat list", Chats(0))
            )
          )
          .some
      )

    val editMessageTextTasksMultiplePages: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        chatId = ChatIntId(0).some,
        messageId = 0.some,
        text =
          "Chat: Kaitrin\n1. 5 – John\n2. 6 – John\n3. 7 – John\n4. 8 – John\n5. 9 – John\n\nSelect the task number to mark it as completed.",
        entities = TypedMessageEntity.toMessageEntities(
          List(
            plain"Chat: ",
            bold"Kaitrin",
            lineBreak,
            plain"1. 5",
            italic" – John",
            lineBreak,
            plain"2. 6",
            italic" – John",
            lineBreak,
            plain"3. 7",
            italic" – John",
            lineBreak,
            plain"4. 8",
            italic" – John",
            lineBreak,
            plain"5. 9",
            italic" – John",
            lineBreak,
            lineBreak,
            italic"Select the task number to mark it as completed."
          )
        ),
        replyMarkup = InlineKeyboardMarkup(
          List(
            List(
              inlineKeyboardButton("1", CheckTask(1, 23L)),
              inlineKeyboardButton("2", CheckTask(1, 24L)),
              inlineKeyboardButton("3", CheckTask(1, 25L)),
              inlineKeyboardButton("4", CheckTask(1, 26L)),
              inlineKeyboardButton("5", CheckTask(1, 27L))
            ),
            List(inlineKeyboardButton("Previous page", Tasks(0, kaitrin.id))),
            List(inlineKeyboardButton("Next page", Tasks(2, kaitrin.id))),
            List(inlineKeyboardButton("Chat list", Chats(0)))
          )
        ).some
      )

    val editMessageTextTasksKaitrin: Method[Either[Boolean, Message]] =
      Methods.editMessageText(
        "Chat: Kaitrin\n\nSelect the task number to mark it as completed.",
        ChatIntId(0).some,
        messageId = 0.some,
        entities = TypedMessageEntity.toMessageEntities(
          List(
            plain"Chat: ",
            bold"Kaitrin",
            lineBreak,
            lineBreak,
            italic"Select the task number to mark it as completed."
          )
        ),
        replyMarkup = InlineKeyboardMarkups.singleButton(inlineKeyboardButton("Chat list", Chats(0))).some
      )

    val taskCompletedByJohnMessage: Method[Message] =
      Methods.sendMessage(
        ChatIntId(kaitrinChatId),
        """Task "Buy some milk" has been marked as completed by John."""
      )

    val taskCompletedByKaitrinMessage: Method[Message] =
      Methods.sendMessage(
        ChatIntId(johnChatId),
        """Task "Buy some milk" has been marked as completed by Kaitrin."""
      )
