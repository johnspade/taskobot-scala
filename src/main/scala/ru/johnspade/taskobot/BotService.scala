package ru.johnspade.taskobot

import cats.implicits._
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.StringMessageEntity.{Bold, Italic, Plain}
import ru.johnspade.taskobot.core.{Page, StringMessageEntity}
import ru.johnspade.taskobot.i18n.{Language, messages}
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.{BotTask, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags._
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.editMessageText
import telegramium.bots.high.implicits._
import telegramium.bots.{ChatIntId, Message}
import zio._
import zio.interop.catz._
import ru.johnspade.taskobot.core.TelegramOps.toUser

object BotService {
  type BotService = Has[Service]

  trait Service {
    def updateUser(tgUser: telegramium.bots.User, chatId: Option[ChatId] = None): UIO[User]

    def listTasks(`for`: User, collaborator: User, pageNumber: PageNumber, message: Message)(
      implicit languageId: LanguageId
    ): UIO[Unit]
  }

  val live: URLayer[UserRepository with TaskRepository with TelegramBotApi, BotService] =
    ZLayer.fromServices[UserRepository.Service, TaskRepository.Service, Api[Task], Service](new LiveService(_, _)(_))

  final class LiveService(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service
  )(implicit api: Api[Task]) extends Service {
    override def updateUser(tgUser: bots.User, chatId: Option[ChatId] = None): UIO[User] =
      userRepo.createOrUpdate(toUser(tgUser, chatId))

    override def listTasks(`for`: User, collaborator: User, pageNumber: PageNumber, message: Message)(
      implicit languageId: LanguageId
    ): UIO[Unit] = {
      Page.request[BotTask, UIO](pageNumber, DefaultPageSize, taskRepo.findShared(`for`.id, collaborator.id))
        .flatMap { page =>
          val chatName = if (collaborator.id == `for`.id) Messages.personalTasks() else collaborator.fullName
          val header = List(Plain(t"Chat: "), Bold(chatName), Plain("\n"))
          val taskList = page
            .items
            .map(_.text)
            .zipWithIndex
            .flatMap { case (text, i) =>
              val senderName = if (collaborator.id == `for`.id) Plain("") else Italic(s"– ${collaborator.firstName}")
              List(Plain(s"${i + 1}. $text"), senderName, Plain("\n"))
            }
          val footer = List(Plain("\n"), Italic("Select the task number to mark it as completed."))
          val messageEntities = header ++ taskList ++ footer

          editMessageText(
            ChatIntId(message.chat.id).some,
            message.messageId.some,
            text = messageEntities.map(_.value).mkString,
            entities = StringMessageEntity.toMessageEntities(messageEntities),
            replyMarkup = Keyboards.tasks(page, collaborator).some
          )
            .exec
            .orDie
            .void
        }
    }
  }
}
