package ru.johnspade.taskobot

import cats.syntax.option._
import io.scalaland.chimney.dsl._
import org.mockito.MockitoSugar
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskId, TaskText}
import ru.johnspade.taskobot.task.{BotTask, NewTask, TaskRepository, TaskRepositoryMock}
import ru.johnspade.taskobot.user.tags.{FirstName, LastName, UserId}
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity._
import ru.makkarpov.scalingua.LanguageId
import zio.ZLayer
import zio.test.Assertion._
import zio.test.TestAspect.sequential
import zio.test._
import zio.test.environment.TestEnvironment
import zio.test.mock.Expectation._

object BotServiceSpec extends DefaultRunnableSpec with MockitoSugar {
  private val userRepo = UserRepository.live
  private val taskRepo = TaskRepository.live
  private val repositories = userRepo ++ taskRepo
  private val botService = repositories >>> BotService.live
  private val testEnv = TestEnvironments.itLayer >+> (repositories ++ botService)

  override def spec: ZSpec[TestEnvironment, Throwable] = suite("BotServiceSpec")(
    (suite("updateUser")(
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
    ) @@ sequential)
      .provideCustomLayerShared(testEnv),

    suite("getTasks")(
      testM("should return tasks") {
        val task1 = NewTask(UserId(322), TaskText("task1"), CreatedAt(0L), alice.id.some)
        val task2 = NewTask(UserId(322), TaskText("task2"), CreatedAt(1L), alice.id.some)
        implicit val languageId: LanguageId = ru.makkarpov.scalingua.Language.English.id
        val expectedTasks = List(
          task2.into[BotTask].withFieldConst(_.id, TaskId(2L)).transform,
          task1.into[BotTask].withFieldConst(_.id, TaskId(1L)).transform
        )
        val expectedMessageEntities = List(
          plain"Chat: ", bold"Alice", lineBreak,
          plain"1. task2", italic" – Bob", lineBreak,
          plain"2. task1", italic" – Bob", lineBreak,
          lineBreak, italic"Select the task number to mark it as completed."
        )
        for {
          _ <- UserRepository.createOrUpdate(bob)
          _ <- UserRepository.createOrUpdate(alice)
          _ <- TaskRepository.create(task1)
          _ <- TaskRepository.create(task2)
          (page, messageEntities) <- BotService.getTasks(bob, alice, PageNumber(0))
        } yield assert(page.items)(equalTo(expectedTasks)) &&
          assert(messageEntities)(equalTo(expectedMessageEntities)) &&
          assert(page.number)(equalTo(PageNumber(0)))
      }
        .provideCustomLayerShared(testEnv),

      testM("should cut long tasks") {
        val tasks = List.tabulate(5) { n =>
          BotTask(TaskId(n.toLong), bob.id, TaskText(longTaskText), alice.id.some, CreatedAt(0L))
        }
        val tasksExp = TaskRepositoryMock.FindShared(anything, value(tasks))
        implicit val languageId: LanguageId = ru.makkarpov.scalingua.Language.English.id
        val app = BotService.getTasks(bob, alice, PageNumber(0))
        for {
          result <- app.provideLayer(ZLayer.succeed(mock[UserRepository.Service]) ++ tasksExp >>> BotService.live)
          entitiesAssertions = assert(result._2)(equalTo {
            List(
              plain"Chat: ", bold"Alice", lineBreak,
              plain"1. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob", lineBreak,
              plain"2. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob", lineBreak,
              plain"3. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob", lineBreak,
              plain"4. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob", lineBreak,
              plain"5. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob", lineBreak,
              lineBreak, italic"Select the task number to mark it as completed."
            )
          })
          lengthAssertions = assert(result._2.map(_.text).mkString.length)(isLessThanEqualTo(MessageLimit))
        } yield entitiesAssertions && lengthAssertions
      },

      testM("should not cut long tasks if message limits are not exceeded") {
        val tasks = BotTask(TaskId(0L), bob.id, TaskText(longTaskText), alice.id.some, CreatedAt(0L)) ::
          List.tabulate(4) { n =>
            BotTask(TaskId((n + 1).toLong), bob.id, TaskText("Fix bugs"), alice.id.some, CreatedAt(0L))
          }
        val tasksExp = TaskRepositoryMock.FindShared(anything, value(tasks))
        implicit val languageId: LanguageId = ru.makkarpov.scalingua.Language.English.id
        val app = BotService.getTasks(bob, alice, PageNumber(0))
        for {
          result <- app.provideLayer(ZLayer.succeed(mock[UserRepository.Service]) ++ tasksExp >>> BotService.live)
          assertions = assert(result._2)(equalTo {
            List(
              plain"Chat: ", bold"Alice", lineBreak,
              plain"1. $longTaskText", italic" – Bob", lineBreak,
              plain"2. Fix bugs", italic" – Bob", lineBreak,
              plain"3. Fix bugs", italic" – Bob", lineBreak,
              plain"4. Fix bugs", italic" – Bob", lineBreak,
              plain"5. Fix bugs", italic" – Bob", lineBreak,
              lineBreak, italic"Select the task number to mark it as completed."
            )
          })
        } yield assertions
      }
    )
  )

  private val bob = User(UserId(322), FirstName("Bob"), Language.English)
  private val alice = User(UserId(911), FirstName("Alice"), Language.English)
  private val longTaskText =
    "Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultrices malesuada arcu."
}
