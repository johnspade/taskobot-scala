package ru.johnspade.taskobot

import cats.syntax.option.*
import ru.johnspade.taskobot.messages.{Language, MessageServiceLive, MsgConfig}
import ru.johnspade.taskobot.task.{BotTask, NewTask, TaskRepository, TaskRepositoryLive}
import ru.johnspade.taskobot.user.{User, UserRepository, UserRepositoryLive}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import zio.*
import zio.test.TestAspect.sequential
import zio.test.*

object BotServiceSpec extends ZIOSpecDefault:
  private val testEnv = ZLayer.make[BotService with UserRepository with TaskRepository](
    TestDatabase.layer,
    UserRepositoryLive.layer,
    TaskRepositoryLive.layer,
    MsgConfig.live,
    MessageServiceLive.layer,
    BotServiceLive.layer
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("BotServiceSpec")(
    (suite("updateUser")(
      test("should convert and create a Telegram user") {
        val tgUser       = telegramium.bots.User(1337, isBot = false, "John")
        val expectedUser = User(1337L, "John", Language.English)
        for
          user      <- BotService.updateUser(tgUser)
          savedUser <- UserRepository.findById(1337L)
        yield assertTrue(user == expectedUser) && assertTrue(savedUser.contains(expectedUser))
      },
      test("should convert and update a Telegram user") {
        val tgUser       = telegramium.bots.User(1337, isBot = false, "John", "Spade".some)
        val expectedUser = User(1337L, "John", Language.English, lastName = "Spade".some)
        for
          _         <- UserRepository.createOrUpdate(User(1337L, "John", Language.English))
          user      <- BotService.updateUser(tgUser)
          savedUser <- UserRepository.findById(1337L)
        yield assertTrue(user == expectedUser) && assertTrue(savedUser.contains(expectedUser))
      }
    ) @@ sequential)
      .provideCustomShared(testEnv),
    suite("getTasks")(
      test("should return tasks") {
        val task1 = NewTask(322L, "task1", 0L, alice.id.some)
        val task2 = NewTask(322L, "task2", 1L, alice.id.some)
        val expectedTasks = List(
          BotTask(2L, task2.sender, task2.text, task2.receiver, task2.createdAt),
          BotTask(1L, task1.sender, task1.text, task1.receiver, task1.createdAt)
        )
        val expectedMessageEntities = List(
          plain"Chat: ",
          bold"Alice",
          lineBreak,
          plain"1. task2",
          italic" – Bob",
          lineBreak,
          plain"2. task1",
          italic" – Bob",
          lineBreak,
          lineBreak,
          italic"Select the task number to mark it as completed."
        )
        for
          _                      <- UserRepository.createOrUpdate(bob)
          _                      <- UserRepository.createOrUpdate(alice)
          _                      <- TaskRepository.create(task1)
          _                      <- TaskRepository.create(task2)
          pageAndMessageEntities <- BotService.getTasks(bob, alice, 0)
        yield assertTrue(pageAndMessageEntities._1.items == expectedTasks) &&
          assertTrue(pageAndMessageEntities._2 == expectedMessageEntities) &&
          assertTrue(pageAndMessageEntities._1.number == 0)
      }
        .provideCustomShared(testEnv),
      test("should cut long tasks") {
        val tasks = List.tabulate(5) { _ =>
          NewTask(sender = bob.id, text = longTaskText, createdAt = 0L, receiver = alice.id.some)
        }
        for
          _      <- UserRepository.createOrUpdate(bob)
          _      <- UserRepository.createOrUpdate(alice)
          _      <- ZIO.foreachDiscard(tasks)(TaskRepository.create)
          result <- BotService.getTasks(bob, alice, 0)
          entitiesAssertions = assertTrue(
            result._2 == List(
              plain"Chat: ",
              bold"Alice",
              lineBreak,
              plain"1. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob",
              lineBreak,
              plain"2. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob",
              lineBreak,
              plain"3. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob",
              lineBreak,
              plain"4. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob",
              lineBreak,
              plain"5. Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor u...",
              italic" – Bob",
              lineBreak,
              lineBreak,
              italic"Select the task number to mark it as completed."
            )
          )
          lengthAssertions = assertTrue(result._2.map(_.text).iterator.mkString("").length <= MessageLimit)
        yield entitiesAssertions && lengthAssertions
      }.provideCustomShared(testEnv),
      test("should not cut long tasks if message limits are not exceeded") {
        val tasks = NewTask(bob.id, longTaskText, 0L, alice.id.some) ::
          List.tabulate(4) { _ =>
            NewTask(bob.id, "Fix bugs", 0L, alice.id.some)
          }
        for
          _      <- UserRepository.createOrUpdate(bob)
          _      <- UserRepository.createOrUpdate(alice)
          _      <- ZIO.foreachDiscard(tasks)(TaskRepository.create)
          result <- BotService.getTasks(bob, alice, 0)
          assertions = assertTrue(
            result._2 == List(
              plain"Chat: ",
              bold"Alice",
              lineBreak,
              plain"1. $longTaskText",
              italic" – Bob",
              lineBreak,
              plain"2. Fix bugs",
              italic" – Bob",
              lineBreak,
              plain"3. Fix bugs",
              italic" – Bob",
              lineBreak,
              plain"4. Fix bugs",
              italic" – Bob",
              lineBreak,
              plain"5. Fix bugs",
              italic" – Bob",
              lineBreak,
              lineBreak,
              italic"Select the task number to mark it as completed."
            )
          )
        yield assertions
      }
        .provideCustomShared(testEnv)
    )
  )

  private val bob   = User(322L, "Bob", Language.English)
  private val alice = User(911L, "Alice", Language.English)
  private val longTaskText =
    "Integer est enim, tincidunt at molestie a, egestas nec sem. Curabitur ac varius turpis. Aenean dui arcu, ultricies nec finibus eu, lobortis eu augue. Morbi vel semper dui, eget hendrerit justo. Aliquam convallis condimentum malesuada. Donec justo leo, dictum sed consectetur vel, convallis sit amet mauris. Cras feugiat quis diam et scelerisque. Sed vel imperdiet sem. Pellentesque fermentum, neque ac sodales dignissim, enim tortor auctor sem, et tempor nulla tortor sed magna. Maecenas dapibus leo vel fringilla tempor. Donec a laoreet ligula. Cras convallis libero vel gravida faucibus. Curabitur ipsum sem, tincidunt sed nisi ut, blandit aliquet mauris. Nunc vulputate orci non maximus dictum. Morbi ultricies mi mi, vel accumsan eros ullamcorper ac. In turpis arcu, porttitor eget tortor ut, ultrices malesuada arcu."
