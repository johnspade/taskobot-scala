package ru.johnspade.taskobot.task

import cats.effect.Resource
import ru.johnspade.taskobot.SessionPool.SessionPool
import ru.johnspade.taskobot.tags.{Offset, PageSize}
import ru.johnspade.taskobot.task.tags._
import ru.johnspade.taskobot.user.tags.UserId
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
    def create(task: NewTask): UIO[BotTask]

    def getUserTasks(id1: UserId, id2: UserId, offset: Offset, limit: PageSize): UIO[List[BotTask]]
  }

  val live: URLayer[SessionPool, TaskRepository] = ZLayer.fromService[Resource[Task, Session[Task]], Service] {
    sessionPool =>
      import TaskQueries._

      new Service {
        override def create(task: NewTask): UIO[BotTask] =
          sessionPool.use {
            _.prepare(insert).use(_.unique(task))
          }
            .orDie

        override def getUserTasks(id1: UserId, id2: UserId, offset: Offset, limit: PageSize): UIO[List[BotTask]] = {
          sessionPool.use {
            _.prepare(selectByUserId).use {
              _.stream(id1 ~ id2 ~ id2 ~ id1 ~ offset ~ limit, limit)
                .compile
                .toList
            }
          }
            .orDie
        }
      }
  }
}

private object TaskQueries {
  val newTaskCodec: Codec[NewTask] =
    (UserId.lift(int4) ~ TaskText.lift(varchar(4096)) ~ CreatedAt.lift(int8) ~ UserId.lift(int4).opt ~ Done.lift(bool)).imap {
      case senderId ~ text ~ createdAt ~ receiver ~ done => NewTask(senderId, text, createdAt, receiver, done)
    }(t => t.sender ~ t.text ~ t.createdAt ~ t.receiver ~ t.done)

  val botTaskCodec: Codec[BotTask] = (
    TaskId.lift(int8) ~
      UserId.lift(int4) ~
      TaskText.lift(varchar(4096)) ~
      UserId.lift(int4).opt ~
      CreatedAt.lift(int8) ~
      DoneAt.lift(int8).opt ~
      Done.lift(bool)
    ).imap { case id ~ senderId ~ text ~ receiverId ~ createdAt ~ doneAt ~ done =>
    BotTask(id, senderId, text, receiverId, createdAt, doneAt, done)
  }(t => t.id ~ t.sender ~ t.text ~ t.receiver ~ t.createdAt ~ t.doneAt ~ t.done)

  val insert: Query[NewTask, BotTask] =
    sql"""
      insert into tasks (sender_id, text, created_at, receiver_id, done) values ($newTaskCodec)
      returning id, sender_id, text, receiver_id, created_at, done_at, done
    """.query(botTaskCodec)

  val selectByUserId: Query[UserId ~ UserId ~ UserId ~ UserId ~ Offset ~ PageSize, BotTask] =
    sql"""
      select from tasks
      where receiver_id is not null and done <> true and
      ((sender_id = ${UserId.lift(int4)} and receiver_id = ${UserId.lift(int4)}) or
       (sender_id = ${UserId.lift(int4)} and receiver_id = ${UserId.lift(int4)}))
      order by created_at desc
      offset ${Offset.lift(int8)} limit ${PageSize.lift(int4)}
    """.query(botTaskCodec)
}
