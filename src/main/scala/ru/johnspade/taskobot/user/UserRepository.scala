package ru.johnspade.taskobot.user

import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import zio.*
import zio.interop.catz.*
import ru.johnspade.taskobot.user.UserRepositoryLive.UserQueries.*

trait UserRepository:
  def findById(id: Long): Task[Option[User]]

  def createOrUpdate(user: User): Task[User]

  def createOrUpdateWithLanguage(user: User): Task[User]

  def findUsersWithSharedTasks(id: Long)(offset: Long, limit: Int): Task[List[User]]

  def clear(): Task[Unit]

object UserRepository:
  def findById(id: Long): ZIO[UserRepository, Throwable, Option[User]] =
    ZIO.serviceWithZIO(_.findById(id))

  def createOrUpdate(user: User): ZIO[UserRepository, Throwable, User] =
    ZIO.serviceWithZIO(_.createOrUpdate(user))

  def createOrUpdateWithLanguage(user: User): ZIO[UserRepository, Throwable, User] =
    ZIO.serviceWithZIO(_.createOrUpdateWithLanguage(user))

  def findUsersWithSharedTasks(id: Long)(offset: Long, limit: Int): ZIO[UserRepository, Throwable, List[User]] =
    ZIO.serviceWithZIO(_.findUsersWithSharedTasks(id)(offset, limit))

  def clear(): ZIO[UserRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.clear())

class UserRepositoryLive(xa: Transactor[Task]) extends UserRepository:
  override def findById(id: Long): Task[Option[User]] =
    selectById(id).option
      .transact(xa)

  override def createOrUpdate(user: User): Task[User] =
    upsert(user).unique
      .transact(xa)

  override def createOrUpdateWithLanguage(user: User): Task[User] =
    upsertWithLanguage(user).unique
      .transact(xa)

  override def findUsersWithSharedTasks(id: Long)(offset: Long, limit: Int): Task[List[User]] =
    selectBySharedTasks(id, offset, limit)
      .to[List]
      .transact(xa)

  override def clear(): Task[Unit] =
    deleteAll.run.void
      .transact(xa)

object UserRepositoryLive:
  val layer: URLayer[DbTransactor, UserRepository] =
    ZLayer(
      ZIO
        .service[DbTransactor]
        .map(new UserRepositoryLive(_))
    )

  object UserQueries {
    def selectById(id: Long): Query0[User] =
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

    def selectBySharedTasks(id: Long, offset: Long, limit: Int): Query0[User] =
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
