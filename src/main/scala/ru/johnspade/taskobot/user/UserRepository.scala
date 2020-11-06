package ru.johnspade.taskobot.user

import cats.syntax.all._
import ru.johnspade.taskobot.DbSession.DbSession
import ru.johnspade.taskobot.l10n.Language
import ru.johnspade.taskobot.user.tags.{UserId, _}
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Session, _}
import zio.interop.catz._
import zio.macros.accessible
import zio.{Has, Task, UIO, URLayer, ZIO, ZLayer}

@accessible
object UserRepository {
  type UserRepository = Has[Service]

  trait Service {
    def findById(userId: UserId): UIO[Option[User]]

    def createOrUpdate(user: User): UIO[Unit]
  }

  val live: URLayer[DbSession, UserRepository] = ZLayer.fromServiceManaged[Session[Task], Any, Nothing, Service] { session =>
    import UserQueries._

    (for {
      implicit0(r: zio.Runtime[Any]) <- ZIO.runtime[Any].toManaged_
      selectByIdPrepared <- session.prepare(selectById).toManaged
      upsertPrepared <- session.prepare(upsert).toManaged
    } yield {
      new Service {
        override def findById(userId: UserId): UIO[Option[User]] =
          selectByIdPrepared.option(userId).orDie

        override def createOrUpdate(user: User): UIO[Unit] =
          upsertPrepared.execute(user).void.orDie
      }
    })
      .orDie
  }

  private object UserQueries {
    val userCodec: Codec[User] = (
      UserId.lift(int8) ~
        FirstName.lift(varchar) ~
        LastName.lift(varchar).opt ~
        ChatId.lift(int8).opt ~
        varchar
      ).imap {
      case id ~ firstName ~ lastName ~ chatId ~ language =>
        User(id, firstName, lastName, chatId, Language.withName(language))
    }(u => u.id ~ u.firstName ~ u.lastName ~ u.chatId ~ u.language.entryName)

    val selectById: Query[UserId, User] =
      sql"""
        select id, first_name, last_name, chat_id, language
        from users
        where id = ${UserId.lift(int8)}
      """.query(userCodec)

    val upsert: Command[User] =
      sql"""
        insert into users values ($userCodec)
        on conflict(id) do update set
        first_name = excluded.first_name, last_name = excluded.last_name, chat_id = excluded.chat_id
      """.command
  }
}
