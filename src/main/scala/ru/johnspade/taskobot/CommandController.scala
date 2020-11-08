package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.ChangeLanguage
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.ChatId
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.implicits._
import telegramium.bots.high.{Api, InlineKeyboardButton, InlineKeyboardMarkup}
import telegramium.bots.{ChatIntId, Html, Message}
import zio._
import zio.macros.accessible

@accessible
object CommandController {
  type CommandController = Has[Service]

  trait Service {
    def onStartCommand(message: Message): UIO[Option[Method[Message]]]

    def onHelpCommand(message: Message): UIO[Option[Method[Message]]]

    def onSettingsCommand(message: Message): UIO[Option[Method[Message]]]
  }

  val live: URLayer[TelegramBotApi with BotService, CommandController] =
    ZLayer.fromServices[Api[Task], BotService.Service, Service] { (api, botService) =>
      new LiveService(botService)(api)
    }

  final class LiveService(private val botService: BotService.Service)(implicit bot: Api[Task]) extends Service {
    override def onStartCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        createHelpMessage(message).exec.orDie.as(createSettingsMessage(message, user))
      }

    override def onHelpCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        ZIO.succeed(createHelpMessage(message))
      }

    override def onSettingsCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        ZIO.succeed(createSettingsMessage(message, user))
      }

    private def withSender(message: Message)(handle: User => UIO[Method[Message]]): UIO[Option[Method[Message]]] =
      ZIO.foreach(message.from)(botService.updateUser(_, ChatId(message.chat.id).some).flatMap(handle(_)))

    private def createHelpMessage(message: Message)(implicit languageId: LanguageId) =
      sendMessage(
        ChatIntId(message.chat.id),
        Messages.help(),
        Html.some,
        disableWebPagePreview = true.some,
        replyMarkup = InlineKeyboardMarkup.singleButton(InlineKeyboardButton.switchInlineQuery(Messages.tasksStart(), "")).some
      )

    private def createSettingsMessage(message: Message, user: User)(implicit languageId: LanguageId) = {
      val languageName = user.language.languageName
      sendMessage(
        ChatIntId(message.chat.id),
        t"Current language: $languageName",
        replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton(t"Switch language", ChangeLanguage)).some
      )
    }
  }
}
