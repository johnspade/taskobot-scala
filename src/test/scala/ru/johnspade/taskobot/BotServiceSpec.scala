package ru.johnspade.taskobot

import ru.johnspade.taskobot.MigrationAspects.migrate
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.task.{BotTask, NewTask, TaskRepository}
import ru.johnspade.taskobot.user.tags.{FirstName, LastName, UserId}
import ru.johnspade.taskobot.user.{User, UserRepository}
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment
import cats.syntax.option._
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskId, TaskText}
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.{Chat, Message}
import io.scalaland.chimney.dsl._
import ru.johnspade.taskobot.core.TypedMessageEntity._

object BotServiceSpec extends DefaultRunnableSpec {
  private val userRepo = UserRepository.live
  private val taskRepo = TaskRepository.live
  private val repositories = userRepo ++ taskRepo
  private val botService = repositories >>> BotService.live
  private val testEnv = TestEnvironments.itLayer >+> (repositories ++ botService)

  override def spec: ZSpec[TestEnvironment, Nothing] = (suite("BotServiceSpec")(
    suite("updateUser")(
      testM("should convert and create a Telegram user") {
        val tgUser = telegramium.bots.User(1337, isBot = false, "John")
        val expectedUser = User(UserId(1337), FirstName("John"), Language.English)
        for {
          user <- BotService.updateUser(tgUser)
          savedUser <- UserRepository.findById(UserId(1337))
        } yield assert(user)(equalTo(expectedUser)) && assert(savedUser)(isSome(equalTo(expectedUser)))
      },
      testM("should convert and update a Telegram user") {
        val tgUser = telegramium.bots.User(1337, isBot = false, "John", "Spade".some)
        val expectedUser = User(UserId(1337), FirstName("John"), Language.English, lastName = LastName("Spade").some)
        for {
          _ <- UserRepository.createOrUpdate(User(UserId(1337), FirstName("John"), Language.English))
          user <- BotService.updateUser(tgUser)
          savedUser <- UserRepository.findById(UserId(1337))
        } yield assert(user)(equalTo(expectedUser)) && assert(savedUser)(isSome(equalTo(expectedUser)))
      }
    ),

    suite("getTasks")(
      testM("should return tasks") {
        val tgUser = telegramium.bots.User(322, isBot = false, "Bob")
        val bob = User(UserId(322), FirstName("Bob"), Language.English)
        val alice = User(UserId(911), FirstName("Alice"), Language.English)
        val task1 = NewTask(UserId(322), TaskText("task1"), CreatedAt(0L), UserId(911).some)
        val task2 = NewTask(UserId(322), TaskText("task2"), CreatedAt(1L), UserId(911).some)
        val message = Message(
          messageId = 0,
          date = 0,
          chat = Chat(id = 0, `type` = "private"),
          from = tgUser.some,
          text = "".some
        )
        implicit val languageId: LanguageId = ru.makkarpov.scalingua.Language.English.id
        val expectedTasks = List(
          task2.into[BotTask].withFieldConst(_.id, TaskId(2L)).transform,
          task1.into[BotTask].withFieldConst(_.id, TaskId(1L)).transform
        )
        val expectedMessageEntities = List(
          plain"Chat: ", bold"Alice", plain"\n",
          plain"1. task2", italic"– Alice", plain"\n",
          plain"2. task1", italic"– Alice", plain"\n",
          plain"\n", italic"Select the task number to mark it as completed."
        )
        for {
          _ <- UserRepository.createOrUpdate(bob)
          _ <- UserRepository.createOrUpdate(alice)
          _ <- TaskRepository.create(task1)
          _ <- TaskRepository.create(task2)
          (page, messageEntities) <- BotService.getTasks(bob, alice, PageNumber(0), message)
        } yield assert(page.items)(equalTo(expectedTasks)) &&
          assert(messageEntities)(equalTo(expectedMessageEntities)) &&
          assert(page.number)(equalTo(PageNumber(0)))
      }
    )
  ) @@ migrate).provideCustomLayerShared(testEnv)
}
