package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.core.TypedMessageEntity._
import ru.johnspade.taskobot.core.{Page, TypedMessageEntity}
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.{BotTask, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags._
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots
import telegramium.bots.Message
import zio._
import zio.interop.catz._
import zio.macros.accessible

@accessible
object BotService {
  type BotService = Has[Service]

  trait Service {
    def updateUser(tgUser: telegramium.bots.User, chatId: Option[ChatId] = None): UIO[User]

    def getTasks(`for`: User, collaborator: User, pageNumber: PageNumber, message: Message)(
      implicit languageId: LanguageId
    ): UIO[(Page[BotTask], List[TypedMessageEntity])]
  }

  val live: URLayer[UserRepository with TaskRepository, BotService] =
    ZLayer.fromServices[UserRepository.Service, TaskRepository.Service, Service](new LiveService(_, _))

  final class LiveService(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service
  ) extends Service {
    override def updateUser(tgUser: bots.User, chatId: Option[ChatId] = None): UIO[User] =
      userRepo.createOrUpdate(toUser(tgUser, chatId))

    override def getTasks(`for`: User, collaborator: User, pageNumber: PageNumber, message: Message)(
      implicit languageId: LanguageId
    ): UIO[(Page[BotTask], List[TypedMessageEntity])] = {
      Page.request[BotTask, UIO](pageNumber, DefaultPageSize, taskRepo.findShared(`for`.id, collaborator.id))
        .map { page =>
          val chatName = if (collaborator.id == `for`.id) Messages.personalTasks() else collaborator.fullName
          val header = List(Plain(t"Chat: "), Bold(chatName), plain"\n")
          val taskList = page
            .items
            .map(_.text)
            .zipWithIndex
            .flatMap { case (text, i) =>
              val senderName = if (collaborator.id == `for`.id) plain"" else italic"– ${collaborator.firstName}"
              List(plain"${i + 1}. $text", senderName, plain"\n")
            }
          val footer = List(plain"\n", italic"Select the task number to mark it as completed.")
          val messageEntities = header ++ taskList ++ footer

          (page, messageEntities)
        }
    }
  }
}
