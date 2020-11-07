package ru.johnspade.taskobot

import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.user.tags._
import telegramium.bots
import zio.{Has, UIO, URLayer, ZLayer}

object BotService {
  type BotService = Has[Service]

  trait Service {
    def updateUser(tgUser: telegramium.bots.User, chatId: Option[ChatId] = None): UIO[User]
  }

  val live: URLayer[UserRepository, BotService] = ZLayer.fromService[UserRepository.Service, Service](new LiveBotService(_))

  final class LiveBotService(private val userRepo: UserRepository.Service) extends Service {
    override def updateUser(tgUser: bots.User, chatId: Option[ChatId] = None): UIO[User] = {
      val language = tgUser.languageCode.filter(_.startsWith("ru")).fold[Language](Language.English)(_ => Language.Russian)
      val user = User(
        id = UserId(tgUser.id.toLong),
        firstName = FirstName(tgUser.firstName),
        lastName = tgUser.lastName.map(LastName(_)),
        chatId = chatId,
        language = language
      )
      userRepo.createOrUpdate(user).as(user)
    }
  }
}
