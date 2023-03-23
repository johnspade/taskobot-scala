package ru.johnspade.taskobot

import cats.syntax.option.*
import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.TestBotApi.Mocks
import ru.johnspade.taskobot.TestBotApi.createMock
import ru.johnspade.taskobot.TestHelpers.createMessage
import ru.johnspade.taskobot.TestUsers.*
import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.core.Chats
import ru.johnspade.taskobot.core.CheckTask
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.taskobot.core.RemoveTaskDeadline
import ru.johnspade.taskobot.core.TaskDeadlineDate
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.Tasks
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.datetime.DatePickerServiceLive
import ru.johnspade.taskobot.datetime.DateTimeControllerLive
import ru.johnspade.taskobot.datetime.TimePickerServiceLive
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig
import ru.johnspade.taskobot.settings.SettingsControllerLive
import ru.johnspade.taskobot.task.TaskControllerLive
import ru.johnspade.taskobot.task.TaskRepositoryLive
import ru.johnspade.taskobot.user.UserRepositoryLive
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import telegramium.bots.*
import telegramium.bots.client.Method
import telegramium.bots.high.Methods
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.keyboards.KeyboardButtons
import zio.*
import zio.test.TestAspect.sequential
import zio.test.*

import java.time.Instant
import java.time.LocalDate

object TaskobotISpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment with Scope, Any] = (suite("TaskobotISpec")(
    test("collaborative tasks") {
      val typeTask =
        for
          inlineQueryReply <- withTaskobotService(
            _.onInlineQueryReply(InlineQuery("0", johnTg, query = "Buy some milk", offset = "0"))
          )
          assertions = assertTrue(
            inlineQueryReply.contains(
              Methods.answerInlineQuery(
                "0",
                cacheTime = 0.some,
                results = List(
                  InlineQueryResultArticle(
                    "1",
                    "Create task",
                    InputTextMessageContent(
                      "Buy some milk",
                      entities = TypedMessageEntity.toMessageEntities(List(Bold("Buy some milk")))
                    ),
                    InlineKeyboardMarkups
                      .singleButton(
                        inlineKeyboardButton("Confirm task", ConfirmTask(id = None, senderId = john.id.some))
                      )
                      .some,
                    description = "Buy some milk".some
                  )
                )
              )
            )
          )
        yield assertions

      val createTask =
        for
          chosenInlineResultReply <-
            withTaskobotService(
              _.onChosenInlineResultReply(
                ChosenInlineResult("0", johnTg, query = "Buy some milk", inlineMessageId = "0".some)
              )
            )
          expectedEditMessageReplyMarkupReq = Methods.editMessageReplyMarkup(
            inlineMessageId = "0".some,
            replyMarkup = InlineKeyboardMarkups
              .singleButton(
                inlineKeyboardButton("Confirm task", ConfirmTask(1L.some, john.id.some))
              )
              .some
          )
        yield assertTrue(chosenInlineResultReply.get.payload == expectedEditMessageReplyMarkupReq.payload)

      val confirmTask = sendCallbackQuery(
        ConfirmTask(1L.some, john.id.some),
        kaitrinTg,
        inlineMessageId = "0".some
      )
        .map(confirmTaskReply => assertTrue(confirmTaskReply.contains(Methods.answerCallbackQuery("0"))))

      val listChats =
        for
          listReply <- sendMessage("/list", isCommand = true, chatId = kaitrinChatId)
          assertions = assertTrue(
            listReply.contains(
              Methods.sendMessage(
                ChatIntId(kaitrinChatId),
                "Chats with tasks",
                replyMarkup = InlineKeyboardMarkups
                  .singleColumn(
                    List(
                      inlineKeyboardButton("Kaitrin", Tasks(firstPage, kaitrin.id)),
                      InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
                    )
                  )
                  .some
              )
            )
          )
        yield assertions

      val listTasks = sendCallbackQuery(Tasks(firstPage, john.id), kaitrinTg, chatId = kaitrinChatId).map(tasksReply =>
        assertTrue(tasksReply.contains(Methods.answerCallbackQuery("0")))
      )

      val getTaskDetails = sendCallbackQuery(TaskDetails(1L, firstPage)).map { detailsReply =>
        assertTrue(detailsReply.contains(Methods.answerCallbackQuery("0")))
      }

      val setDeadlineDate = sendCallbackQuery(TaskDeadlineDate(1L, LocalDate.EPOCH)).map { detailsReply =>
        assertTrue(detailsReply.contains(Methods.answerCallbackQuery("0")))
      }

      val setDeadlineTime = sendCallbackQuery(TimePicker(1L, 0.some, 0.some, confirm = true)).map { detailsReply =>
        assertTrue(detailsReply.contains(Methods.answerCallbackQuery("0")))
      }

      val removeDeadline = sendCallbackQuery(RemoveTaskDeadline(1L)).map { detailsReply =>
        assertTrue(detailsReply.contains(Methods.answerCallbackQuery("0")))
      }

      val checkTask =
        sendCallbackQuery(CheckTask(firstPage, 1L), kaitrinTg, chatId = kaitrinChatId).map(checkTaskReply =>
          assertTrue(
            checkTaskReply.contains(Methods.answerCallbackQuery("0", "Task has been marked completed.".some))
          )
        )

      for
        _                      <- TestClock.setTime(Instant.EPOCH)
        now                    <- Clock.instant
        _                      <- createMock(Mocks.addConfirmButton, Mocks.messageResponse)
        _                      <- createMock(Mocks.removeReplyMarkup, Mocks.messageResponse)
        _                      <- createMock(Mocks.editMessageTextList, Mocks.messageResponse)
        _                      <- createMock(Mocks.editMessageTextCheckTask(kaitrinChatId), Mocks.messageResponse)
        _                      <- createMock(Mocks.taskCompletedMessage, Mocks.messageResponse)
        _                      <- createMock(Mocks.editMessageTextTaskDetails(1L, now), Mocks.messageResponse)
        _                      <- createMock(Mocks.editMessageTextTaskDeadlineUpdated(1L, now), Mocks.messageResponse)
        _                      <- createMock(Mocks.editMessageTextTimePicker(1L, now), Mocks.messageResponse)
        _                      <- createMock(Mocks.editMessageTextTaskDeadlineRemoved(1L, now), Mocks.messageResponse)
        typeTaskAssertions     <- typeTask
        createTaskAssertions   <- createTask
        confirmTaskAssertions  <- confirmTask
        listChatsAssertions    <- listChats
        listTasksAssertions    <- listTasks
        taskDetailsAssertions  <- getTaskDetails
        deadlineDateAssertions <- setDeadlineDate
        deadlineTimeAssertions <- setDeadlineTime
        noDeadlineAssertions   <- removeDeadline
        checkTaskAssertions    <- checkTask
      yield typeTaskAssertions &&
        createTaskAssertions &&
        confirmTaskAssertions &&
        listChatsAssertions &&
        listTasksAssertions &&
        taskDetailsAssertions &&
        deadlineDateAssertions &&
        deadlineTimeAssertions &&
        noDeadlineAssertions &&
        checkTaskAssertions
    },
    test("personal tasks") {
      val start =
        for
          startMessage <- sendMessage("/start", isCommand = true)
          assertions = assertTrue(
            startMessage.contains(
              Methods.sendMessage(
                ChatIntId(johnChatId),
                "Taskobot is a task collaboration bot. Type <code>@tasko_bot task</code> in any private chat and " +
                  "select <b>Create task</b>. After receiver's confirmation, a collaborative task will be created. " +
                  "Type /list in the bot chat to see your tasks.\n\nSwitch language: /settings\n" +
                  "Support a creator: https://buymeacoff.ee/johnspade ☕\n\n" +
                  "Forward messages here to create personal tasks.",
                parseMode = Html.some,
                replyMarkup = expectedMenu,
                disableWebPagePreview = true.some
              )
            )
          )
        yield assertions

      val create =
        for
          createReply <- sendMessage("/create", isCommand = true)
          assertions = assertTrue(
            createReply.contains(
              Methods.sendMessage(
                ChatIntId(johnChatId),
                "/create: New personal task",
                replyMarkup = ForceReply(forceReply = true).some
              )
            )
          )
        yield assertions

      val sendTask = sendMessage(
        "Buy groceries",
        replyToMessage = mockMessage().copy(text = "/create: New personal task".some).some
      )
        .map { personalTaskReply =>
          assertTrue(
            personalTaskReply.contains(
              Methods.sendMessage(
                ChatIntId(johnChatId),
                "Chat: Personal tasks\n1. Buy groceries\n",
                entities = TypedMessageEntity.toMessageEntities(
                  List(
                    plain"Chat: ",
                    bold"Personal tasks",
                    lineBreak,
                    plain"1. Buy groceries",
                    italic"",
                    lineBreak
                  )
                ),
                replyMarkup = InlineKeyboardMarkups
                  .singleColumn(
                    List(
                      inlineKeyboardButton("1", TaskDetails(2L, firstPage)),
                      inlineKeyboardButton("Chat list", Chats(firstPage))
                    )
                  )
                  .some
              )
            )
          )
        }

      val checkTask = sendCallbackQuery(CheckTask(firstPage, 2L))
        .map { checkTaskReply =>
          assertTrue(
            checkTaskReply.contains(Methods.answerCallbackQuery("0", "Task has been marked completed.".some))
          )
        }

      for
        _ <- createMock(
          Mocks.taskCreatedMessage("""Personal task "Buy groceries" has been created."""),
          Mocks.messageResponse
        )
        _                   <- createMock(Mocks.editMessageTextPersonalTasks, Mocks.messageResponse)
        startAssertions     <- start
        createAssertions    <- create
        sendTaskAssertions  <- sendTask
        checkTaskAssertions <- checkTask
      yield startAssertions &&
        createAssertions &&
        sendTaskAssertions &&
        checkTaskAssertions
    },
    test("forwards") {
      val forward = {
        val forwardedMessage = Message(
          messageId = 0,
          date = 0,
          chat = Chat(id = johnChatId, `type` = "private"),
          from = johnTg.some,
          text = "Watch Firefly".some,
          entities = List.empty,
          forwardDate = 0.some,
          forwardFrom = johnTg.some,
          forwardSenderName = "John".some
        )
        withTaskobotService(_.onMessageReply(forwardedMessage))
          .map { forwardReply =>
            assertTrue(
              forwardReply.contains(
                Methods.sendMessage(
                  ChatIntId(johnChatId),
                  "Chat: Personal tasks\n1. Watch Firefly – John\n",
                  entities = TypedMessageEntity.toMessageEntities(
                    List(
                      plain"Chat: ",
                      bold"Personal tasks",
                      lineBreak,
                      plain"1. Watch Firefly",
                      italic" – John",
                      lineBreak
                    )
                  ),
                  replyMarkup = InlineKeyboardMarkups
                    .singleColumn(
                      List(
                        inlineKeyboardButton("1", TaskDetails(3L, firstPage)),
                        inlineKeyboardButton("Chat list", Chats(firstPage))
                      )
                    )
                    .some
                )
              )
            )
          }
      }

      val checkTask = sendCallbackQuery(CheckTask(firstPage, 3L))
        .map { checkTaskReply =>
          assertTrue(
            checkTaskReply.contains(Methods.answerCallbackQuery("0", "Task has been marked completed.".some))
          )
        }

      for
        _ <- createMock(
          Mocks.taskCreatedMessage("""Personal task "Watch Firefly" has been created."""),
          Mocks.messageResponse
        )
        _                   <- createMock(Mocks.editMessageTextPersonalTasks, Mocks.messageResponse)
        forwardAssertions   <- forward
        checkTaskAssertions <- checkTask
      yield forwardAssertions &&
        checkTaskAssertions
    },
    test("Ignore should be ignored") {
      for
        ignoreReply <- sendCallbackQuery(Ignore)
        assertions = assertTrue(ignoreReply == Methods.answerCallbackQuery("0").some)
      yield assertions
    }
  ) @@ sequential).provideCustomShared(env)

  private val firstPage = 0

  private val expectedMenu = ReplyKeyboardMarkup(
    List(
      List(KeyboardButtons.text("\uD83D\uDCCB Tasks"), KeyboardButtons.text("➕ New personal task")),
      List(KeyboardButtons.text("\uD83D\uDE80 New collaborative task"), KeyboardButtons.text("❓ Help")),
      List(
        KeyboardButtons.text("⚙️ Settings"),
        KeyboardButton("🌍 Timezone", webApp = Some(WebAppInfo("https://timezones.johnspade.ru")))
      )
    ),
    resizeKeyboard = true.some
  ).some

  private def mockMessage(chatId: Int = 0) =
    Message(0, date = 0, chat = Chat(chatId, `type` = ""), from = User(id = 123, isBot = true, "Taskobot").some)

  private def withTaskobotService(f: Taskobot => Task[Option[Method[_]]]) =
    ZIO.serviceWithZIO[Taskobot](f)

  private def sendMessage(
      text: String,
      isCommand: Boolean = false,
      chatId: Int = johnChatId,
      replyToMessage: Option[Message] = None
  ) =
    withTaskobotService(_.onMessageReply(createMessage(text, isCommand, chatId, replyToMessage)))

  private def sendCallbackQuery(
      data: CbData,
      from: User = johnTg,
      inlineMessageId: Option[String] = None,
      chatId: Int = 0
  ) =
    withTaskobotService {
      _.onCallbackQueryReply(
        CallbackQuery(
          "0",
          from,
          chatInstance = "",
          data = data.toCsv.some,
          message = mockMessage(chatId).some,
          inlineMessageId = inlineMessageId
        )
      )
    }

  private val env = ZLayer.make[MockServerClient with Taskobot](
    TestDatabase.layer,
    TestBotApi.testApiLayer,
    UserRepositoryLive.layer,
    TaskRepositoryLive.layer,
    MsgConfig.live,
    MessageServiceLive.layer,
    KeyboardServiceLive.layer,
    BotServiceLive.layer,
    CommandControllerLive.layer,
    TaskControllerLive.layer,
    SettingsControllerLive.layer,
    DatePickerServiceLive.layer,
    TimePickerServiceLive.layer,
    DateTimeControllerLive.layer,
    IgnoreControllerLive.layer,
    UserMiddleware.live,
    BotConfig.live,
    Taskobot.live
  )
