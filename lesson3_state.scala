// Lesson 3: Stateful Computation with State
//
// The State effect models mutable state in a controlled way.
// State.run(initialValue) { ... } returns (finalState, result).

import in.rcard.yaes.*

@main def lesson3(): Unit =
  println("=== Lesson 3: Stateful Computation with State ===")
  println()

  // --- Basic State.get and State.set ---
  println("--- State.get / State.set ---")
  val (s1, r1) = State.run(0) {
    val before = State.get[Int]      // read current state
    State.set(10)                    // overwrite state
    val after = State.get[Int]       // read updated state
    s"was $before, now $after"
  }
  println(s"Final state: $s1, Result: $r1") // Final state: 10, Result: was 0, now 10
  println()

  // --- State.update: transform the state with a function ---
  println("--- State.update ---")
  val (s2, r2) = State.run(1) {
    State.update[Int](_ + 1)   // state = 2
    State.update[Int](_ * 10)  // state = 20
    State.update[Int](_ + 3)   // state = 23
    State.get[Int]
  }
  println(s"After updates — state: $s2, result: $r2") // 23, 23
  println()

  // --- State.use: read-only view of state ---
  // State.use(f) applies f to the current state WITHOUT modifying it
  println("--- State.use ---")
  val (s3, r3) = State.run(List("hello", "world", "yaes")) {
    val count = State.use[List[String], Int](_.length)
    val upper = State.use[List[String], List[String]](_.map(_.toUpperCase))
    val csv   = State.use[List[String], String](_.mkString(", "))
    (count, upper, csv)
  }
  println(s"State unchanged: $s3")
  println(s"Derived values:  $r3")
  println()

  // --- Counter example: recursive stateful computation ---
  println("--- Recursive Counter ---")
  def countUp()(using State[Int]): String =
    val current = State.get[Int]
    if current >= 5 then s"Reached $current!"
    else
      State.update[Int](_ + 1)
      countUp()

  val (counterState, counterResult) = State.run(0) { countUp() }
  println(s"$counterResult (final state: $counterState)") // Reached 5! (final state: 5)
  println()

  // --- Key-value store example ---
  println("--- Key-Value Store ---")
  type Store = Map[String, Int]

  val (store, total) = State.run(Map.empty[String, Int]) {
    State.update[Store](_ + ("apples" -> 3))
    State.update[Store](_ + ("bananas" -> 5))
    State.update[Store](_ + ("oranges" -> 2))

    val sum = State.use[Store, Int](_.values.sum)

    // Remove an item
    State.update[Store](_ - "bananas")

    sum
  }
  println(s"Total before removal: $total")  // 10
  println(s"Final store: $store")           // Map(apples -> 3, oranges -> 2)
  println()

  println("=== End of Lesson 3 ===")
