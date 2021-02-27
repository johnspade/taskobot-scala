package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.{BotTask, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags._
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity._
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots
import zio._
import zio.interop.catz._
import zio.macros.accessible

@accessible
object BotService {
  type BotService = Has[Service]

  trait Service {
    def updateUser(tgUser: telegramium.bots.User, chatId: Option[ChatId] = None): Task[User]

    def getTasks(`for`: User, collaborator: User, pageNumber: PageNumber)(
      implicit languageId: LanguageId
    ): Task[(Page[BotTask], List[TypedMessageEntity])]
  }

  val live: URLayer[UserRepository with TaskRepository, BotService] =
    ZLayer.fromServices[UserRepository.Service, TaskRepository.Service, Service](new LiveService(_, _))

  final class LiveService(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service
  ) extends Service {
    override def updateUser(tgUser: bots.User, chatId: Option[ChatId] = None): Task[User] =
      userRepo.createOrUpdate(toUser(tgUser, chatId))

    override def getTasks(`for`: User, collaborator: User, pageNumber: PageNumber)(
      implicit languageId: LanguageId
    ): Task[(Page[BotTask], List[TypedMessageEntity])] = {
      Page.request[BotTask, Task](pageNumber, DefaultPageSize, taskRepo.findShared(`for`.id, collaborator.id))
        .map { page =>
          val chatName = if (collaborator.id == `for`.id) Messages.personalTasks() else collaborator.fullName
          val header = List(Plain(t"Chat" + ": "), Bold(chatName), lineBreak)
          val taskList = page
            .items
            .zipWithIndex
            .flatMap { case (task, i) =>
              val senderName = if (collaborator.id == `for`.id) {
                task.forwardFromSenderName
                  .map(n => italic" – $n")
                  .getOrElse(plain"")
              } else
                italic" – ${collaborator.firstName}"
              List(plain"${i + 1}. ${task.text}", senderName, lineBreak)
            }
          val footer = List(lineBreak, italic"Select the task number to mark it as completed.")
          val messageEntities = header ++ taskList ++ footer

          (page, messageEntities)
        }
    }
  }
}
