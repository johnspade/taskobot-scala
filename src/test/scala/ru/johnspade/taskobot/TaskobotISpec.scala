package ru.johnspade.taskobot

import cats.syntax.option._
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import ru.johnspade.taskobot.Configuration.BotConfig
import ru.johnspade.taskobot.MigrationAspects.migrate
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.Taskobot.{LiveTaskobot, Taskobot}
import ru.johnspade.taskobot.TestAssertions.isMethodsEqual
import ru.johnspade.taskobot.TestEnvironments.PostgresITEnv
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.TypedMessageEntity._
import ru.johnspade.taskobot.core.{CbData, ChangeLanguage, Chats, CheckTask, ConfirmTask, Tasks, TypedMessageEntity}
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.tags.TaskId
import ru.johnspade.taskobot.task.{TaskController, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.tags.UserId
import telegramium.bots.client.Method
import telegramium.bots.high.{Api, InlineKeyboardButton, InlineKeyboardMarkup, Methods}
import telegramium.bots.{CallbackQuery, Chat, ChatIntId, ChosenInlineResult, ForceReply, Html, InlineQuery, InlineQueryResultArticle, InputTextMessageContent, KeyboardMarkup, Markdown2, Message, MessageEntity, ParseMode, User}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.Assertion.{equalTo, isSome}
import zio.test.TestAspect.sequential
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Task, URLayer, ZIO, ZLayer}

object TaskobotISpec extends DefaultRunnableSpec with MockitoSugar with ArgumentMatchersSugar {
  override def spec: ZSpec[TestEnvironment, Throwable] = (suite("TaskobotISpec")(
    testM("collaborative tasks") {
      val typeTask =
        for {
          inlineQueryReply <- withTaskobotService(_.onInlineQueryReply(InlineQuery("0", john, query = "Buy some milk", offset = "0")))
          assertions = assert(inlineQueryReply)(isSome(equalTo {
            Methods.answerInlineQuery(
              "0", cacheTime = 0.some,
              results = List(
                InlineQueryResultArticle(
                  "1", "Create task", InputTextMessageContent("*Buy some milk*", Markdown2.some),
                  InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Confirm task", ConfirmTask(id = None))).some,
                  description = "Buy some milk".some
                )
              )
            )
          }))
        } yield assertions

      val createTask =
        for {
          chosenInlineResultReply <-
            withTaskobotService(_.onChosenInlineResultReply(ChosenInlineResult("0", john, query = "Buy some milk", inlineMessageId = "0".some)))
          expectedEditMessageReplyMarkupReq = Methods.editMessageReplyMarkup(
            inlineMessageId = "0".some,
            replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Confirm task", ConfirmTask(TaskId(1L).some))).some
          )
        } yield assert(chosenInlineResultReply)(isSome(isMethodsEqual(expectedEditMessageReplyMarkupReq)))

      val confirmTask =
        for {
          confirmTaskReply <- sendCallbackQuery(ConfirmTask(TaskId(1L).some), kaitrin, inlineMessageId = "0".some)
          removeMarkupAssertions = verifyMethodCall(botApiMock, Methods.editMessageReplyMarkup(inlineMessageId = "0".some, replyMarkup = None))
          confirmTaskReplyAssertions = assert(confirmTaskReply)(isSome(equalTo(Methods.answerCallbackQuery("0"))))
        } yield removeMarkupAssertions && confirmTaskReplyAssertions

      val listChats =
        for {
          listReply <- sendMessage("/list", isCommand = true, chatId = kaitrinChatId)
          assertions = assert(listReply)(isSome(equalTo(Methods.sendMessage(
            ChatIntId(kaitrinChatId),
            "Chats with tasks",
            replyMarkup =
              InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Kaitrin", Tasks(UserId(kaitrinId), firstPage))).some
          ))))
        } yield assertions

      val listTasks =
        for {
          tasksReply <- sendCallbackQuery(Tasks(UserId(johnId), firstPage), kaitrin, chatId = kaitrinChatId)
          listTasksAssertions = verifyMethodCall(botApiMock, Methods.editMessageText(
            ChatIntId(kaitrinChatId).some,
            messageId = 0.some,
            text = "Chat: John\\n1. Buy some milk– John\\n\\nSelect the task number to mark it as completed.",
            entities = TypedMessageEntity.toMessageEntities(List(
              plain"Chat: ", bold"John", plain"\n",
              plain"1. Buy some milk", italic"– John", plain"\n",
              plain"\n", italic"Select the task number to mark it as completed."
            )),
            replyMarkup = InlineKeyboardMarkup.singleColumn(List(
              inlineKeyboardButton("1", CheckTask(TaskId(1L), firstPage, UserId(johnId))),
              inlineKeyboardButton("Chat list", Chats(firstPage))
            )).some
          ))
          tasksReplyAssertions = assert(tasksReply)(isSome(equalTo(Methods.answerCallbackQuery("0"))))
        } yield listTasksAssertions && tasksReplyAssertions

      val checkTask =
        for {
          checkTaskReply <- sendCallbackQuery(CheckTask(TaskId(1L), firstPage, UserId(johnId)), kaitrin, chatId = 1)
          _ <- ZIO.effect(Thread.sleep(1000))
          noTasksAssertions = verifyMethodCall(botApiMock, Methods.editMessageText(
            ChatIntId(kaitrinChatId).some,
            messageId = 0.some,
            text = "Chat: John\\n\\nSelect the task number to mark it as completed.",
            entities = TypedMessageEntity.toMessageEntities(List(
              plain"Chat: ", bold"John", plain"\n",
              plain"\n", italic"Select the task number to mark it as completed."
            )),
            replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Chat list", Chats(firstPage))).some
          ))
          checkTaskReplyAssertions =
          assert(checkTaskReply)(isSome(equalTo(Methods.answerCallbackQuery("0", "Task has been marked as completed.".some))))
          _ = verifySendMessage("Task \"Buy some milk\" has been marked as completed by Kaitrin.", chatId = kaitrinChatId)
        } yield noTasksAssertions && checkTaskReplyAssertions

      for {
        typeTaskAssertions <- typeTask
        createTaskAssertions <- createTask
        confirmTaskAssertions <- confirmTask
        listChatsAssertions <- listChats
        listTasksAssertions <- listTasks
        checkTaskAssertions <- checkTask
      } yield typeTaskAssertions &&
        createTaskAssertions &&
        confirmTaskAssertions &&
        listChatsAssertions &&
        listTasksAssertions &&
        checkTaskAssertions
    },

    testM("personal tasks") {
      val start =
        for {
          startReply <- sendMessage("/start", isCommand = true)
          _ = verifySendMessage(
            "Taskobot is a task collaboration bot. You can type <code>@tasko_bot task</code> in private chat and " +
              "select <b>Create task</b>. After receiver's confirmation collaborative task will be created. " +
              "Type /list in the bot chat to see your tasks.\n\nSupport a creator: https://buymeacoff.ee/johnspade ☕",
            InlineKeyboardMarkup.singleButton(InlineKeyboardButton.switchInlineQuery("Start creating tasks", "")).some,
            Html.some,
            disableWebPagePreview = true.some
          )
        } yield assert(startReply)(isSome(equalTo(Methods.sendMessage(
          ChatIntId(johnChatId),
          "Current language: English",
          replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Switch language", ChangeLanguage)).some
        ))))

      val create =
        for {
          createReply <- sendMessage("/create", isCommand = true)
          assertions = assert(createReply)(isSome(equalTo(Methods.sendMessage(
            ChatIntId(johnChatId),
            "/create: New personal task",
            replyMarkup = ForceReply(forceReply = true).some
          ))))
        } yield assertions

      val sendTask =
        for {
          personalTaskReply <- sendMessage(
            "Buy groceries",
            replyToMessage = mockMessage().copy(text = "/create: New personal task".some).some
          )
          assertions = assert(personalTaskReply)(isSome(equalTo(Methods.sendMessage(
            ChatIntId(johnChatId), """Personal task "Buy groceries" has been created."""
          ))))
        } yield assertions

      val listChats =
        for {
          listReply <- sendMessage("/list", isCommand = true)
          assertions = assert(listReply)(isSome(equalTo(Methods.sendMessage(
            ChatIntId(johnChatId),
            "Chats with tasks",
            replyMarkup =
              InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Personal tasks", Tasks(UserId(johnId), firstPage))).some
          ))))
        } yield assertions

      val listTasks =
        for {
          tasksReply <- sendCallbackQuery(Tasks(UserId(johnId), firstPage))
          _ = verifyMethodCall(botApiMock, Methods.editMessageText(
            ChatIntId(johnChatId).some,
            messageId = 0.some,
            text = "Chat: Personal tasks\\n1. Buy groceries\\n\\nSelect the task number to mark it as completed.",
            entities = TypedMessageEntity.toMessageEntities(List(
              plain"Chat: ", bold"Personal tasks", plain"\n",
              plain"1. Buy groceries", plain"\n",
              plain"\n", italic"Select the task number to mark it as completed."
            )),
            replyMarkup = InlineKeyboardMarkup.singleColumn(List(
              inlineKeyboardButton("1", CheckTask(TaskId(2L), firstPage, UserId(johnId))),
              inlineKeyboardButton("Chat list", Chats(firstPage))
            )).some
          ))
        } yield assert(tasksReply)(isSome(equalTo(Methods.answerCallbackQuery("0"))))

      val checkTask =
        for {
          checkTaskReply <- sendCallbackQuery(CheckTask(TaskId(2L), firstPage, UserId(johnId)))
          _ = verifyMethodCall(botApiMock, Methods.editMessageText(
            ChatIntId(johnChatId).some,
            messageId = 0.some,
            text = "Chat: Personal tasks\\n\\nSelect the task number to mark it as completed.",
            entities = TypedMessageEntity.toMessageEntities(List(
              plain"Chat: ", bold"Personal tasks", plain"\n",
              plain"\n", italic"Select the task number to mark it as completed."
            )),
            replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Chat list", Chats(firstPage))).some
          ))
        } yield assert(checkTaskReply)(isSome(equalTo(Methods.answerCallbackQuery("0", "Task has been marked as completed.".some))))

      for {
        startAssertions <- start
        createAssertions <- create
        sendTaskAssertions <- sendTask
        listChatsAssertions <- listChats
        listTasksAssertions <- listTasks
        checkTaskAssertions <- checkTask
      } yield startAssertions &&
        createAssertions &&
        sendTaskAssertions &&
        listChatsAssertions &&
        listTasksAssertions &&
        checkTaskAssertions
    }
  ) @@ migrate @@ sequential).provideCustomLayerShared(TestEnvironment.env)

  private def mockMessage(chatId: Int = 0) =
    Message(0, date = 0, chat = Chat(chatId, `type` = ""), from = User(id = 123, isBot = true, "Taskobot").some)

  private val botApiMock = mock[Api[Task]]
  when(botApiMock.execute[Message](*)).thenReturn(Task.succeed(mockMessage()))
  when(botApiMock.execute[Either[Boolean, Message]](*)).thenReturn(Task.right(mockMessage()))

  private val johnId = 1337
  private val johnChatId = 0
  private val john = User(johnId, isBot = false, "John")
  private val kaitrinId = 911
  private val kaitrinChatId = 1
  private val kaitrin = User(kaitrinId, isBot = false, "Kaitrin")

  private val firstPage = PageNumber(0)

  private def createMessage(text: String, isCommand: Boolean = false, chatId: Int = johnChatId, replyToMessage: Option[Message] = None) =
    Message(
      messageId = 0,
      date = 0,
      chat = Chat(id = chatId, `type` = "private"),
      from = john.some,
      text = text.some,
      entities = if (isCommand) List(MessageEntity("bot_command", offset = 0, length = text.length)) else List.empty,
      replyToMessage = replyToMessage
    )

  private def withTaskobotService(f: LiveTaskobot => Task[Option[Method[_]]]) =
    ZIO.service[LiveTaskobot].flatMap(f)

  private def sendMessage(text: String, isCommand: Boolean = false, chatId: Int = johnChatId, replyToMessage: Option[Message] = None) =
    withTaskobotService(_.onMessageReply(createMessage(text, isCommand, chatId, replyToMessage)))

  private def sendCallbackQuery(data: CbData, from: User = john, inlineMessageId: Option[String] = None, chatId: Int = 0) =
    withTaskobotService {
      _.onCallbackQueryReply(
        CallbackQuery("0", from, chatInstance = "", data = data.toCsv.some, message = mockMessage(chatId).some, inlineMessageId = inlineMessageId)
      )
    }

  private def verifySendMessage(
    text: String,
    markup: Option[KeyboardMarkup] = None,
    parseMode: Option[ParseMode] = None,
    disableWebPagePreview: Option[Boolean] = None,
    chatId: Int = 0
  ): Unit = {
    verify(botApiMock).execute(Methods.sendMessage(
      ChatIntId(chatId),
      text,
      replyMarkup = markup,
      parseMode = parseMode,
      disableWebPagePreview = disableWebPagePreview
    ))
  }

  private def verifyMethodCall[Res](api: Api[Task], method: Method[Res]) = {
    val captor = ArgCaptor[Method[Res]]
    verify(api, atLeastOnce).execute(captor)
    assert(captor.values)(Assertion.exists(isMethodsEqual(method)))
  }

  private object TestEnvironment {
    private val botApi = ZLayer.succeed(botApiMock)
    private val botConfig = ZLayer.succeed(BotConfig(0, "https://example.com", "123"))
    private val userRepo = UserRepository.live
    private val taskRepo = TaskRepository.live
    private val repositories = userRepo ++ taskRepo
    private val botService = repositories >>> BotService.live
    private val commandController = (ZLayer.requires[Clock] ++ botApi ++ botService ++ repositories) >>> CommandController.live
    private val taskController = (ZLayer.requires[Clock] ++ botApi ++ botService ++ repositories) >>> TaskController.live
    private val settingsController = (userRepo ++ botApi) >>> SettingsController.live
    private val userMiddleware = botService >>> UserMiddleware.live
    private val taskobot = (
      ZLayer.requires[SessionPool] ++
        ZLayer.requires[Clock] ++
        botApi ++
        botConfig ++
        taskRepo ++
        botService ++
        commandController ++
        taskController ++
        settingsController ++
        userMiddleware
      ) >>> Taskobot.live
    val env: URLayer[Clock with Blocking, Clock with PostgresITEnv with Taskobot] =
      ZLayer.requires[Clock] ++ TestEnvironments.itLayer >+> taskobot
  }
}