package ru.johnspade.taskobot.task

import cats.effect.ConcurrentEffect
import cats.syntax.option._
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryDsl._
import ru.johnspade.taskobot.core.{CbData, Chats, Page}
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{CbDataUserRoutes, DefaultPageSize, Keyboards, Messages}
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.ChatIntId
import telegramium.bots.high.Methods.editMessageText
import telegramium.bots.high._
import zio._
import zio.interop.catz._

object TaskController {
  type TaskController = Has[Service]

  trait Service {
    def routes: CbDataUserRoutes[Task]
  }

  final class LiveTaskController(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service
  )(
    implicit api: Api[Task],
    CE: ConcurrentEffect[Task]
  ) extends Service {
    override val routes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of[CbData, User, Task] {
      case Chats(pageNumber) in cb as user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        Page.paginate[User, UIO](pageNumber, DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id)).map { page =>
          cb.message.map { msg =>
            editMessageText(
              ChatIntId(msg.chat.id).some,
              msg.messageId.some,
              text = Messages.chatsWithTasks(),
              replyMarkup = Keyboards.chats(page, user).some
            )
          }
        }
    }
  }
}
