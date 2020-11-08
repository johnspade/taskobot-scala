package ru.johnspade.taskobot.user

import cats.effect.Resource
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.user.tags.{UserId, _}
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Session, _}
import zio.interop.catz._
import zio.macros.accessible
import zio._

@accessible
object UserRepository {
  type UserRepository = Has[Service]

  trait Service {
    def findById(userId: UserId): UIO[Option[User]]

    def createOrUpdate(user: User): UIO[User]
  }

  val live: URLayer[SessionPool, UserRepository] = ZLayer.fromService[Resource[Task, Session[Task]], Service] {
    sessionPool =>
      import UserQueries._

      new Service {
        override def findById(userId: UserId): UIO[Option[User]] =
          sessionPool.use {
            _.prepare(selectById).use(_.option(userId))
          }
            .orDie

        override def createOrUpdate(user: User): UIO[User] =
          sessionPool.use {
            _.prepare(upsert).use(_.unique(user))
          }
            .orDie
      }
  }

  private object UserQueries {
    val userCodec: Codec[User] = (
      UserId.lift(int4) ~
        FirstName.lift(varchar(255)) ~
        LastName.lift(varchar(255)).opt ~
        ChatId.lift(int8).opt ~
        varchar(255)
      ).imap {
      case id ~ firstName ~ lastName ~ chatId ~ language =>
        User(id, firstName, lastName, chatId, Language.withName(language))
    }(u => u.id ~ u.firstName ~ u.lastName ~ u.chatId ~ u.language.entryName)

    val selectById: Query[UserId, User] =
      sql"""
        select id, first_name, last_name, chat_id, language
        from users
        where id = ${UserId.lift(int4)}
      """.query(userCodec)

    val upsert: Query[User, User] =
      sql"""
        insert into users (id, first_name, last_name, chat_id, language) values ($userCodec)
        on conflict(id) do update set
        first_name = excluded.first_name, last_name = excluded.last_name, chat_id = excluded.chat_id
        returning id, first_name, last_name, chat_id, language
      """.query(userCodec)
  }
}
