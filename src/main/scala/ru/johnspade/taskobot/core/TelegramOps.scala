package ru.johnspade.taskobot.core

import java.time.ZoneId

import zio.*

import telegramium.bots.CallbackQuery
import telegramium.bots.InaccessibleMessage
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.MaybeInaccessibleMessage
import telegramium.bots.Message
import telegramium.bots.client.Method
import telegramium.bots.high.Api
import telegramium.bots.high.FailedRequest
import telegramium.bots.high.Methods.answerCallbackQuery
import telegramium.bots.high.implicits.*
import telegramium.bots.high.keyboards.InlineKeyboardButtons

import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.user.User

object TelegramOps {
  def inlineKeyboardButton(text: String, cbData: CbData): InlineKeyboardButton =
    InlineKeyboardButtons.callbackData(text, callbackData = cbData.toCsv)

  def toUser(tgUser: telegramium.bots.User, chatId: Option[Long] = None, timezone: Option[ZoneId] = None): User = {
    val language =
      tgUser.languageCode.filter(_.startsWith("ru")).fold[Language](Language.English)(_ => Language.Russian)
    User(
      id = tgUser.id,
      firstName = tgUser.firstName,
      lastName = tgUser.lastName,
      chatId = chatId,
      language = language,
      timezone = timezone,
      // If we got chatId, the bot is not blocked
      blockedBot = chatId.map(_ => false)
    )
  }

  def ackCb(cb: CallbackQuery)(f: Message => Task[Unit]): ZIO[Any, Throwable, Option[Method[Boolean]]] =
    ZIO.foreachDiscard(cb.message.collect { case msg: Message => msg })(f).as(Some(answerCallbackQuery(cb.id)))

  def execDiscardWithHandling[Res](method: Method[Res])(using api: Api[Task]) =
    method.exec.catchSome {
      case err: FailedRequest[Res]
          if err.errorCode.contains(400) && err.description.exists(
            _.contains(
              "Bad Request: message is not modified: specified new message content and reply markup are exactly the same as a current content and reply markup of the message"
            )
          ) =>
        ZIO.logWarning("Bad Request: message is not modified") // ignore this error
    }.unit

  extension (maybeInaccessibleMessage: MaybeInaccessibleMessage)
    def toMessage: Option[Message] = maybeInaccessibleMessage match
      case _: InaccessibleMessage => None
      case m: Message             => Some(m)
}
