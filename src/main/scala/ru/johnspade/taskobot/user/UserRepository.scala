package ru.johnspade.taskobot.user

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.{Offset, PageSize}
import ru.johnspade.taskobot.user.tags.{ChatId, FirstName, LastName, UserId}
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

  val live: URLayer[DbTransactor, UserRepository] = ZLayer.fromService[Transactor[Task], Service] {
    xa =>
      import UserQueries._

      new Service {
        override def findById(id: UserId): Task[Option[User]] =
          selectById(id)
            .option
            .transact(xa)

        override def createOrUpdate(user: User): Task[User] =
          upsert(user)
            .unique
            .transact(xa)

        override def createOrUpdateWithLanguage(user: User): Task[User] =
          upsertWithLanguage(user)
            .unique
            .transact(xa)

        override def findUsersWithSharedTasks(id: UserId)(offset: Offset, limit: PageSize): Task[List[User]] =
          selectBySharedTasks(id, offset, limit)
            .to[List]
            .transact(xa)

        override def clear(): Task[Unit] =
          deleteAll
            .run
            .void
            .transact(xa)
      }
  }

  private object UserQueries {
    private implicit val userRead: Read[User] =
      Read[(Long, String, String, Option[Long], Option[String])].map {
        case (userId, firstName, language, chatIdOpt, lastNameOpt) =>
          User(UserId(userId), FirstName(firstName), Language.withValue(language), chatIdOpt.map(ChatId(_)), lastNameOpt.map(LastName(_)))
      }

    private implicit val userWrite: Write[User] =
      Write[(Long, String, String, Option[Long], Option[String])].contramap { u =>
        (u.id, u.firstName, u.language.value, u.chatId, u.lastName)
      }

    def selectById(id: UserId): Query0[User] =
      sql"""
        select id, first_name, language, chat_id, last_name
        from users
        where id = $id
      """
        .query[User]

    def upsert(user: User): Query0[User] = {
      import user._

      sql"""
        insert into users (id, first_name, language, chat_id, last_name)
        values ($id, $firstName, ${language.value}, $chatId, $lastName)
        on conflict(id) do update set
        first_name = excluded.first_name, last_name = excluded.last_name, chat_id = coalesce(users.chat_id, excluded.chat_id)
        returning id, first_name, language, chat_id, last_name
      """
        .query[User]
    }

    def upsertWithLanguage(user: User): Query0[User] = {
      import user._

      sql"""
        insert into users (id, first_name, language, chat_id, last_name)
        values ($id, $firstName, ${language.value}, $chatId, $lastName)
        on conflict(id) do update set
        first_name = excluded.first_name, last_name = excluded.last_name, chat_id = coalesce(users.chat_id, excluded.chat_id), language = excluded.language
        returning id, first_name, language, chat_id, last_name
      """
        .query[User]
    }

    def selectBySharedTasks(id: UserId, offset: Offset, limit: PageSize): Query0[User] =
      sql"""
        select distinct u.id, u.first_name, u.language, u.chat_id, u.last_name
        from users as u
                 join (select t3.collaborator, t3.created_at
                       from (
                                select t2.collaborator,
                                       t2.created_at,
                                       max(t2.created_at) over (partition by t2.collaborator) as max_created_at
                                from (
                                         select case when sender_id = $id then receiver_id else sender_id end as collaborator,
                                                created_at
                                         from tasks t1
                                         where (t1.receiver_id = $id or t1.sender_id = $id)
                                           and t1.receiver_id is not null
                                           and t1.done <> true) t2) t3
                       where max_created_at = t3.created_at
                       order by t3.created_at desc) c on u.id = c.collaborator offset $offset limit $limit;
      """
        .query[User]

    val deleteAll: Update0 = sql"delete from users where true".update
  }
}
