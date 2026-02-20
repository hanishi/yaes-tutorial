// Lesson 6: Composing Multiple Effects
//
// Real programs use multiple effects together. YAES lets you declare
// all required effects as context parameters and nest handlers to run them.

import in.rcard.yaes.*

@main def lesson6(): Unit =
  println("=== Lesson 6: Composing Multiple Effects ===")
  println()

  // --- Functions with multiple effects ---
  println("--- Multi-effect functions ---")

  // This function requires Random, Output, and Raise
  def randomGreeting(name: String)(using Random, Output, Raise[String]): String =
    if name.isEmpty then Raise.raise("Name cannot be empty")
    val roll = (Random.nextInt % 3).abs
    val greeting = roll match
      case 0 => s"Hello, $name!"
      case 1 => s"Hey there, $name!"
      case _ => s"Greetings, $name!"
    Output.printLn(greeting)
    greeting

  // Nest handlers to provide all effects
  val result: Either[String, String] = Output.run {
    Random.run {
      Raise.either {
        randomGreeting("Alice")
      }
    }
  }
  println(s"Result: $result")
  println()

  // --- Coin Flip Guessing Game ---
  // Combines: Random (coin flip), State (score tracking),
  //           Output (messages), Raise (game over)
  println("--- Coin Flip Guessing Game ---")

  case class Score(correct: Int, total: Int):
    def hit: Score = Score(correct + 1, total + 1)
    def miss: Score = Score(correct, total + 1)
    override def toString: String = s"$correct/$total"

  def flipCoin()(using Random): Boolean =
    Random.nextBoolean

  def playRound(guess: Boolean)(using Random, State[Score], Output): Unit =
    val coin = flipCoin()
    val coinStr = if coin then "Heads" else "Tails"
    val guessStr = if guess then "Heads" else "Tails"
    if coin == guess then
      Output.printLn(s"  Coin: $coinStr, Guess: $guessStr -> Correct!")
      State.update[Score](_.hit)
    else
      Output.printLn(s"  Coin: $coinStr, Guess: $guessStr -> Wrong!")
      State.update[Score](_.miss)

  def playGame(rounds: Int)(using Random, State[Score], Output): Score =
    for i <- 1 to rounds do
      // Alternate guessing heads and tails for demonstration
      val guess = i % 2 == 0
      playRound(guess)
    State.get[Score]

  // Run the game with all effects composed
  val (finalScore, gameResult) = Output.run {
    Random.run {
      State.run(Score(0, 0)) {
        Output.printLn("Playing 6 rounds of coin flip...")
        playGame(6)
      }
    }
  }
  println(s"Final score: $finalScore")
  println(s"Game result: $gameResult")
  println()

  // --- Nested error handling with multiple effects ---
  println("--- Multi-layer error handling ---")

  sealed trait GameError
  case object TooManyMisses extends GameError
  case class InvalidInput(msg: String) extends GameError

  def strictGame(rounds: Int)(using Random, State[Score], Output, Raise[GameError]): Score =
    for i <- 1 to rounds do
      val guess = flipCoin() // guess randomly
      playRound(guess)
      val score = State.get[Score]
      val misses = score.total - score.correct
      if misses >= 3 then
        Output.printLn(s"  Too many misses ($misses)! Game over.")
        Raise.raise(TooManyMisses)
    State.get[Score]

  val strictResult: Either[GameError, (Score, Score)] = Output.run {
    Random.run {
      Raise.either {
        State.run(Score(0, 0)) {
          Output.printLn("Playing strict game (max 3 misses)...")
          strictGame(10)
        }
      }
    }
  }
  strictResult match
    case Right((finalState, score)) =>
      println(s"Completed! Score: $score")
    case Left(TooManyMisses) =>
      println(s"Game over: too many misses!")
    case Left(InvalidInput(msg)) =>
      println(s"Invalid input: $msg")
  println()

  println("=== End of Lesson 6 ===")
