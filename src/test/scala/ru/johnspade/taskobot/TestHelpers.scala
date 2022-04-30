package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.TestUsers.{johnChatId, johnTg, taskobot}
import ru.johnspade.taskobot.core.CbData
import telegramium.bots.{BotCommandMessageEntity, CallbackQuery, Chat, Message, User => TgUser}

object TestHelpers:
  def mockMessage(chatId: Int = 0): Message =
    Message(0, date = 0, chat = Chat(chatId, `type` = ""), from = taskobot.some)

  def createMessage(
      text: String,
      isCommand: Boolean = false,
      chatId: Int = johnChatId,
      replyToMessage: Option[Message] = None
  ): Message =
    Message(
      messageId = 0,
      date = 0,
      chat = Chat(id = chatId, `type` = "private"),
      from = johnTg.some,
      text = text.some,
      entities = if (isCommand) List(BotCommandMessageEntity(offset = 0, length = text.length)) else List.empty,
      replyToMessage = replyToMessage
    )

  def callbackQuery(data: CbData, from: TgUser, inlineMessageId: Option[String] = None): CallbackQuery =
    CallbackQuery(
      "0",
      from,
      chatInstance = "",
      data = data.toCsv.some,
      message = mockMessage().some,
      inlineMessageId = inlineMessageId
    )
