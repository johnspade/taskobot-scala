package ru.johnspade.taskobot

import cats.effect.ConcurrentEffect
import ru.johnspade.taskobot.Environments.{AppEnvironment, appEnvironment}
import ru.johnspade.taskobot.Taskobot.Taskobot
import zio._
import zio.interop.catz._

object Main extends zio.interop.catz.CatsApp {
  val program: ZIO[AppEnvironment, Throwable, Unit] =
    for {
      _ <- FlywayMigration.migrate
      _ <- Task.concurrentEffect.flatMap { implicit CE: ConcurrentEffect[Task] =>
        blocking.blockingExecutor.flatMap { ec =>
          ZIO.accessM[Taskobot](_.get.start(ec.asEC).toManaged.useForever)
        }
      }
    } yield ()

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program
      .provideSomeLayer(appEnvironment)
      .exitCode
}
