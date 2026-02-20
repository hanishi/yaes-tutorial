// Lesson 5: Random and Clock Effects
//
// Random provides controlled randomness; Clock provides time access.
// Both are effects, making them testable (you can swap implementations).

import in.rcard.yaes.*
import java.time.Instant

@main def lesson5(): Unit =
  println("=== Lesson 5: Random and Clock Effects ===")
  println()

  // --- Random: generating random values ---
  println("--- Random ---")
  Random.run {
    val a = Random.nextInt
    val b = Random.nextInt
    println(s"Two random ints: $a, $b")

    val bool = Random.nextBoolean
    println(s"Random boolean: $bool")

    val dbl = Random.nextDouble
    println(s"Random double: $dbl")

    val lng = Random.nextLong
    println(s"Random long: $lng")
  }
  println()

  // --- Using Random in a function ---
  println("--- Random in functions ---")
  def rollDice()(using Random): Int =
    // nextInt returns any int, so we use modulo to get 1-6
    val raw = Random.nextInt
    (raw % 6).abs + 1

  Random.run {
    val rolls = (1 to 5).map(_ => rollDice())
    println(s"5 dice rolls: ${rolls.mkString(", ")}")
  }
  println()

  // --- Generating a random list ---
  println("--- Random list ---")
  def randomList(size: Int)(using Random): List[Int] =
    (1 to size).map(_ => (Random.nextInt % 100).abs).toList

  Random.run {
    val lst = randomList(8)
    println(s"8 random numbers (0-99): $lst")
  }
  println()

  // --- Clock.now ---
  println("--- Clock ---")
  Clock.run {
    val now: Instant = Clock.now
    println(s"Current time: $now")

    val monotonic = Clock.nowMonotonic
    println(s"Monotonic duration: $monotonic")
  }
  println()

  // --- Using Clock to measure elapsed time ---
  println("--- Measuring elapsed time ---")
  Clock.run {
    val start = Clock.now
    // Simulate some work
    var sum = 0L
    for i <- 1 to 1_000_000 do sum += i
    val end = Clock.now

    val elapsed = java.time.Duration.between(start, end)
    println(s"Sum of 1 to 1,000,000: $sum")
    println(s"Elapsed: ${elapsed.toMillis}ms")
  }
  println()

  // --- Composing Random + Clock ---
  println("--- Random + Clock together ---")
  Random.run {
    Clock.run {
      val timestamp = Clock.now
      val value = Random.nextInt
      println(s"At $timestamp, random value = $value")
    }
  }
  println()

  println("=== End of Lesson 5 ===")
