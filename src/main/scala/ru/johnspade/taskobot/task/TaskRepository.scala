package ru.johnspade.taskobot.task

import cats.effect.Resource
import cats.implicits._
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.{Offset, PageSize}
import ru.johnspade.taskobot.task.tags._
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.{ChatId, FirstName, LastName, UserId}
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, _}
import zio._
import zio.interop.catz._
import zio.macros.accessible

@accessible
object TaskRepository {
  type TaskRepository = Has[Service]

  trait Service {
    def findById(id: TaskId): UIO[Option[BotTask]]

    def findByIdWithCollaborator(id: TaskId, `for`: UserId): UIO[Option[TaskWithCollaborator]]

    def create(task: NewTask): UIO[BotTask]

    def setReceiver(id: TaskId, senderId: Option[UserId], receiverId: UserId): UIO[Unit]

    def findShared(id1: UserId, id2: UserId)(offset: Offset, limit: PageSize): UIO[List[BotTask]]

    def check(id: TaskId, doneAt: DoneAt, userId: UserId): UIO[Unit]

    def clear(): UIO[Unit]
  }

  val live: URLayer[SessionPool, TaskRepository] = ZLayer.fromService[Resource[Task, Session[Task]], Service] {
    sessionPool =>
      import TaskQueries._

      new Service {
        override def findById(id: TaskId): UIO[Option[BotTask]] =
          sessionPool.use {
            _.prepare(selectById).use(_.option(id))
          }
            .orDie

        override def findByIdWithCollaborator(id: TaskId, `for`: UserId): UIO[Option[TaskWithCollaborator]] =
          sessionPool.use {
            _.prepare(selectByIdJoinCollaborator).use(_.option(`for` ~ `for` ~ id ~ `for` ~ `for`))
          }
            .orDie

        override def create(task: NewTask): UIO[BotTask] =
          sessionPool.use {
            _.prepare(insert).use(_.unique(task))
          }
            .orDie

        override def setReceiver(id: TaskId, senderId: Option[UserId], receiverId: UserId): UIO[Unit] =
          sessionPool.use { pool =>
            senderId
              .map { sId =>
                pool.prepare(updateReceiverId).use(_.execute(receiverId ~ id ~ sId))
              }
              .getOrElse {
                pool.prepare(updateReceiverIdWithoutSenderCheck).use(_.execute(receiverId ~ id))
              }
          }
            .void
            .orDie

        override def findShared(id1: UserId, id2: UserId)(offset: Offset, limit: PageSize): UIO[List[BotTask]] =
          sessionPool.use {
            _.prepare(selectByUserId).use {
              _.stream(id1 ~ id2 ~ id2 ~ id1 ~ offset ~ limit, limit)
                .compile
                .toList
            }
          }
            .orDie

        override def check(id: TaskId, doneAt: DoneAt, userId: UserId): UIO[Unit] =
          sessionPool.use {
            _.prepare(setDone).use {
              _.execute(doneAt ~ id ~ userId ~ userId)
            }
          }
            .void
            .orDie

        override def clear(): UIO[Unit] =
          sessionPool.use {
            _.prepare(deleteAll).use {
              _.execute(Void)
            }
          }
            .void
            .orDie
      }
  }
}

private object TaskQueries {
  val newTaskCodec: Codec[NewTask] =
    (
      UserId.lift(int4) ~
        TaskText.lift(varchar(4096)) ~
        CreatedAt.lift(int8) ~
        UserId.lift(int4).opt ~
        Done.lift(bool) ~
        UserId.lift(int4).opt ~
        SenderName.lift(varchar(255)).opt
      ).imap {
      case senderId ~ text ~ createdAt ~ receiver ~ done ~ forwardFromId ~ forwardFromSenderName =>
        NewTask(senderId, text, createdAt, receiver, done, forwardFromId, forwardFromSenderName)
    }(t => t.sender ~ t.text ~ t.createdAt ~ t.receiver ~ t.done ~ t.forwardFromId ~ t.forwardFromSenderName)

  val botTaskCodec: Codec[BotTask] = (
    TaskId.lift(int8) ~
      UserId.lift(int4) ~
      TaskText.lift(varchar(4096)) ~
      UserId.lift(int4).opt ~
      CreatedAt.lift(int8) ~
      DoneAt.lift(int8).opt ~
      Done.lift(bool) ~
      UserId.lift(int4).opt ~
      SenderName.lift(varchar(255)).opt
    ).imap { case id ~ senderId ~ text ~ receiverId ~ createdAt ~ doneAt ~ done ~ forwardFromId ~ forwardFromSenderName =>
    BotTask(id, senderId, text, receiverId, createdAt, doneAt, done, forwardFromId, forwardFromSenderName)
  }(t => t.id ~ t.sender ~ t.text ~ t.receiver ~ t.createdAt ~ t.doneAt ~ t.done ~ t.forwardFromId ~ t.forwardFromSenderName)

  val taskWithCollaboratorDecoder: Decoder[TaskWithCollaborator] = (
    TaskId.lift(int8) ~
      TaskText.lift(varchar(4096)) ~
      UserId.lift(int4).opt ~
      FirstName.lift(varchar(255)).opt ~
      varchar(255).opt ~
      ChatId.lift(int8).opt ~
      LastName.lift(varchar(255)).opt
    )
    .asDecoder
    .map { case id ~ text ~ userIdOpt ~ firstNameOpt ~ languageOpt ~ chatIdOpt ~ lastNameOpt =>
      val collaborator = for {
        userId <- userIdOpt
        firstName <- firstNameOpt
        language <- languageOpt
      } yield User(userId, firstName, Language.withValue(language), chatIdOpt, lastNameOpt)
      TaskWithCollaborator(id, text, collaborator)
    }

  val selectById: Query[TaskId, BotTask] =
    sql"""
      select id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
      from tasks
      where id = ${TaskId.lift(int8)}
    """
      .query(botTaskCodec)

  val selectByIdJoinCollaborator: Query[UserId ~ UserId ~ TaskId ~ UserId ~ UserId, TaskWithCollaborator] =
    sql"""
      select t.id, t.text, u.id, u.first_name, u.language, u.chat_id, u.last_name
      from tasks t
      left join users u on
        (u.id = t.sender_id and u.id <> ${UserId.lift(int4)}) or
        (u.id = t.receiver_id and u.id <> ${UserId.lift(int4)}) or
        (t.sender_id = t.receiver_id and u.id = t.sender_id)
      where t.id = ${TaskId.lift(int8)} and (t.sender_id = ${UserId.lift(int4)} or t.receiver_id = ${UserId.lift(int4)})
    """
      .query(taskWithCollaboratorDecoder)

  val insert: Query[NewTask, BotTask] =
    sql"""
      insert into tasks (sender_id, text, created_at, receiver_id, done, forward_from_id, forward_sender_name) values ($newTaskCodec)
      returning id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
    """
      .query(botTaskCodec)

  val updateReceiverId: Command[UserId ~ TaskId ~ UserId] =
    sql"""
      update tasks
      set receiver_id = ${UserId.lift(int4)}
      where id = ${TaskId.lift(int8)} and sender_id = ${UserId.lift(int4)} and receiver_id is null
    """
      .command

  val updateReceiverIdWithoutSenderCheck: Command[UserId ~ TaskId] =
    sql"""
      update tasks
      set receiver_id = ${UserId.lift(int4)}
      where id = ${TaskId.lift(int8)} and receiver_id is null
    """
      .command

  val selectByUserId: Query[UserId ~ UserId ~ UserId ~ UserId ~ Offset ~ PageSize, BotTask] =
    sql"""
      select id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
      from tasks
      where receiver_id is not null and done <> true and
      ((sender_id = ${UserId.lift(int4)} and receiver_id = ${UserId.lift(int4)}) or
       (sender_id = ${UserId.lift(int4)} and receiver_id = ${UserId.lift(int4)}))
      order by created_at desc
      offset ${Offset.lift(int8)} limit ${PageSize.lift(int4)}
    """
      .query(botTaskCodec)

  val setDone: Command[DoneAt ~ TaskId ~ UserId ~ UserId] =
    sql"""
      update tasks
      set done = true, done_at = ${DoneAt.lift(int8)}
      where id = ${TaskId.lift(int8)} and done = false and
        (sender_id = ${UserId.lift(int4)} or receiver_id = ${UserId.lift(int4)})
    """
      .command

  val deleteAll: Command[Void] = sql"delete from tasks where true".command
}
