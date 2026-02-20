// Lesson 9: Effects and Testability
//
// The biggest practical benefit of an effect system is testability.
// Effects are injected via `using` parameters, so you can swap real
// implementations for mocks (fakes) in tests.
//
// This lesson demonstrates how to make effectful code testable.
// Actual munit tests are in the tests/ directory.
//   scala-cli test .

import in.rcard.yaes.*

@main def lesson9(): Unit =
  println("=== Lesson 9: Effects and Testability ===")
  println()

  // ============================================================
  // First, define the business logic we want to test
  // ============================================================

  // --- Example 1: Validation function ---
  // Functions using only Raise are the easiest to test
  def validateUsername(name: String)(using Raise[String]): String =
    Raise.ensure(name.nonEmpty)("Username is required")
    Raise.ensure(name.length >= 3)("Username must be at least 3 characters")
    Raise.ensure(name.length <= 20)("Username must be at most 20 characters")
    Raise.ensure(name.forall(_.isLetterOrDigit))("Username must be alphanumeric only")
    name

  println("--- Example 1: Testing Raise ---")
  println("Use Raise.either to verify error and success paths:")

  val valid = Raise.either { validateUsername("alice123") }
  val empty = Raise.either { validateUsername("") }
  val short = Raise.either { validateUsername("ab") }
  val bad   = Raise.either { validateUsername("user@name") }

  println(s"  'alice123'  -> $valid")
  println(s"  ''          -> $empty")
  println(s"  'ab'        -> $short")
  println(s"  'user@name' -> $bad")
  // In tests: assert(valid == Right("alice123"))
  println()

  // --- Example 2: Mocking Random ---
  // Functions using Random can be tested by injecting fixed values
  println("--- Example 2: Mocking Random ---")

  def rollDice()(using Random): Int =
    (Random.nextInt % 6).abs + 1

  def gamble()(using Random, Raise[String]): String =
    val roll = rollDice()
    if roll >= 4 then s"Win! (rolled $roll)"
    else Raise.raise(s"Lose... (rolled $roll)")

  // Create a mock Random that always returns the same value
  def fixedRandom(value: Int): Random =
    new Yaes(new Random.Unsafe {
      def nextInt(): Int = value
      def nextBoolean(): Boolean = value % 2 == 0
      def nextDouble(): Double = value.toDouble
      def nextLong(): Long = value.toLong
    })

  // Test the win case (inject nextInt = 3 so roll = 4)
  val winResult = Raise.either {
    given Random = fixedRandom(3) // (3 % 6).abs + 1 = 4
    gamble()
  }
  println(s"  Fixed value 3 -> $winResult") // Right(Win! (rolled 4))

  // Test the lose case (inject nextInt = 0 so roll = 1)
  val loseResult = Raise.either {
    given Random = fixedRandom(0) // (0 % 6).abs + 1 = 1
    gamble()
  }
  println(s"  Fixed value 0 -> $loseResult") // Left(Lose... (rolled 1))
  println()

  // --- Example 3: Mocking Output ---
  // Capture output in a buffer instead of printing to console
  println("--- Example 3: Mocking Output ---")

  def greetUser(name: String)(using Output): Unit =
    Output.printLn(s"Hello, $name!")
    Output.printLn("Have a great day.")

  // Create a mock Output that records to a buffer
  def bufferedOutput(): (Output, () => List[String]) =
    val buffer = scala.collection.mutable.ListBuffer.empty[String]
    val output: Output = new Yaes(new Output.Unsafe {
      def print(text: String): Unit = buffer += text
      def printLn(text: String): Unit = buffer += text
      def printErr(text: String): Unit = ()
      def printErrLn(text: String): Unit = ()
    })
    (output, () => buffer.toList)

  val (testOutput, getLines) = bufferedOutput()
  given Output = testOutput
  greetUser("Alice")

  val captured = getLines()
  println(s"  Captured output: $captured")
  // In tests: assert(captured.contains("Hello, Alice!"))
  println()

  // --- Example 4: Testing State ---
  // State.run returns (finalState, result), so you can assert on both
  println("--- Example 4: Testing State ---")

  def addItems(items: List[String])(using State[List[String]]): Int =
    items.foreach { item =>
      State.update[List[String]](item :: _)
    }
    State.use[List[String], Int](_.length)

  val (finalState, count) = State.run(List.empty[String]) {
    addItems(List("apple", "banana", "orange"))
  }
  println(s"  Final state: $finalState")
  println(s"  Item count: $count")
  // In tests: assert(count == 3) && assert(finalState.contains("apple"))
  println()

  // --- Example 5: Integration test with multiple effects ---
  println("--- Example 5: Multi-effect integration test ---")

  case class Player(name: String, score: Int)

  def playRound()(using Random, State[Player], Output, Raise[String]): Unit =
    val player = State.get[Player]
    val roll = rollDice()
    Output.printLn(s"${player.name} rolled $roll")
    if roll >= 4 then
      State.update[Player](p => p.copy(score = p.score + roll))
    else if player.score + roll < 0 then
      Raise.raise("Score too low")

  // Test with all effects mocked/controlled
  val (testOut2, getLines2) = bufferedOutput()
  val testResult: Either[String, (Player, Unit)] = Raise.either {
    given Output = testOut2
    given Random = fixedRandom(5) // roll = (5 % 6).abs + 1 = 6
    State.run(Player("TestPlayer", 0)) {
      playRound()
    }
  }

  testResult match
    case Right((player, _)) =>
      println(s"  Player: $player")
      println(s"  Output: ${getLines2()}")
    case Left(err) =>
      println(s"  Error: $err")
  // In tests: assert(player.score == 6)
  println()

  // ============================================================
  // Summary
  // ============================================================
  println("--- Summary ---")
  println("Why effects make testing easier:")
  println()
  println("  1. Swappable implementations")
  println("     Effects are `using` parameters, so you inject mocks at test time.")
  println("     e.g., Random -> fixed values, Output -> buffer, Clock -> frozen time")
  println()
  println("  2. Pure business logic")
  println("     Side effects are separated into effects, so the logic itself")
  println("     can be tested as a pure input -> output function.")
  println()
  println("  3. Error path verification")
  println("     Raise.either makes it trivial to test error cases.")
  println("     No try/catch needed — errors are type-safe values.")
  println()
  println("  Run the actual munit tests:")
  println("    scala-cli test .")
  println()

  println("=== End of Lesson 9 ===")
