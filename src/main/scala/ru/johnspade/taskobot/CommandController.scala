package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.user.tags.ChatId
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.{InlineKeyboardButton, InlineKeyboardMarkup}
import telegramium.bots.{ChatIntId, Html, Message}
import zio._
import zio.macros.accessible

@accessible
object CommandController {
  type CommandController = Has[Service]

  trait Service {
    def onHelpCommand(message: Message): UIO[Option[Method[Message]]]
  }

  val live: URLayer[BotService, CommandController] = ZLayer.fromService[BotService.Service, Service](new LiveService(_))

  final class LiveService(private val botService: BotService.Service) extends Service {
    override def onHelpCommand(message: Message): UIO[Option[Method[Message]]] =
      ZIO.foreach(message.from) { tgUser =>
        botService.updateUser(tgUser, ChatId(message.chat.id).some).map { user =>
          implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
          sendMessage(
            ChatIntId(message.chat.id),
            Messages.help(),
            Html.some,
            disableWebPagePreview = true.some,
            replyMarkup = InlineKeyboardMarkup.singleButton(InlineKeyboardButton.switchInlineQuery(Messages.tasksStart(), "")).some
          )
        }
      }
  }
}
