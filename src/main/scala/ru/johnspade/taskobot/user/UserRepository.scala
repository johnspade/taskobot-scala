package ru.johnspade.taskobot.user

import cats.effect.Resource
import cats.implicits._
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.{Offset, PageSize}
import ru.johnspade.taskobot.user.tags.{UserId, _}
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Session, _}
import zio._
import zio.interop.catz._
import zio.macros.accessible

@accessible
object UserRepository {
  type UserRepository = Has[Service]

  trait Service {
    def findById(id: UserId): Task[Option[User]]

    def createOrUpdate(user: User): Task[User]

    def createOrUpdateWithLanguage(user: User): Task[User]

    def findUsersWithSharedTasks(id: UserId)(offset: Offset, limit: PageSize): Task[List[User]]

    def clear(): Task[Unit]
  }

  val live: URLayer[SessionPool, UserRepository] = ZLayer.fromService[Resource[Task, Session[Task]], Service] {
    sessionPool =>
      import UserQueries._

      new Service {
        override def findById(id: UserId): Task[Option[User]] =
          sessionPool.use {
            _.prepare(selectById).use(_.option(id))
          }

        override def createOrUpdate(user: User): Task[User] =
          sessionPool.use {
            _.prepare(upsert).use(_.unique(user))
          }

        override def createOrUpdateWithLanguage(user: User): Task[User] =
          sessionPool.use {
            _.prepare(upsertWithLanguage).use(_.unique(user))
          }

        override def findUsersWithSharedTasks(id: UserId)(offset: Offset, limit: PageSize): Task[List[User]] =
          sessionPool.use {
            _.prepare(selectBySharedTasks).use {
              _.stream(id ~ id ~ id ~ offset ~ limit, 512)
                .compile
                .toList
            }
          }

        override def clear(): Task[Unit] =
          sessionPool.use {
            _.prepare(deleteAll).use {
              _.execute(Void)
            }
          }
            .void
      }
  }

  private object UserQueries {
    val userCodec: Codec[User] = (
      UserId.lift(int4) ~
        FirstName.lift(varchar(255)) ~
        varchar(255) ~
        ChatId.lift(int8).opt ~
        LastName.lift(varchar(255)).opt
      ).imap {
      case id ~ firstName ~ language ~ chatId ~ lastName =>
        User(id, firstName, Language.withValue(language), chatId, lastName)
    }(u => u.id ~ u.firstName ~ u.language.value ~ u.chatId ~ u.lastName)

    val selectById: Query[UserId, User] =
      sql"""
        select id, first_name, language, chat_id, last_name
        from users
        where id = ${UserId.lift(int4)}
      """.query(userCodec)

    val upsert: Query[User, User] =
      sql"""
        insert into users (id, first_name, language, chat_id, last_name) values ($userCodec)
        on conflict(id) do update set
        first_name = excluded.first_name, last_name = excluded.last_name, chat_id = excluded.chat_id
        returning id, first_name, language, chat_id, last_name
      """.query(userCodec)

    val upsertWithLanguage: Query[User, User] =
      sql"""
        insert into users (id, first_name, language, chat_id, last_name) values ($userCodec)
        on conflict(id) do update set
        first_name = excluded.first_name, last_name = excluded.last_name, chat_id = excluded.chat_id, language = excluded.language
        returning id, first_name, language, chat_id, last_name
      """.query(userCodec)

    val selectBySharedTasks: Query[UserId ~ UserId ~ UserId ~ Offset ~ PageSize, User] =
      sql"""
        select distinct u.id, u.first_name, u.language, u.chat_id, u.last_name
        from users as u
                 join (select t3.collaborator, t3.created_at
                       from (
                                select t2.collaborator,
                                       t2.created_at,
                                       max(t2.created_at) over (partition by t2.collaborator) as max_created_at
                                from (
                                         select case when sender_id = ${UserId.lift(int4)} then receiver_id else sender_id end as collaborator,
                                                created_at
                                         from tasks t1
                                         where (t1.receiver_id = ${UserId.lift(int4)} or t1.sender_id = ${UserId.lift(int4)})
                                           and t1.receiver_id is not null
                                           and t1.done <> true) t2) t3
                       where max_created_at = t3.created_at
                       order by t3.created_at desc) c on u.id = c.collaborator offset ${Offset.lift(int8)} limit ${PageSize.lift(int4)};
      """.query(userCodec)

    val deleteAll: Command[Void] = sql"delete from users where true".command
  }
}
