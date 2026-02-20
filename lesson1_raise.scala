// Lesson 1: Typed Error Handling with Raise
//
// The Raise effect lets you short-circuit computations with typed errors,
// similar to Either but in direct style — no flatMap chains needed.

import in.rcard.yaes.*
import in.rcard.yaes.Raise.*
import scala.util.Try

@main def lesson1(): Unit =
  println("=== Lesson 1: Typed Error Handling with Raise ===")
  println()

  // --- Raise.run: returns A | E (union type) ---
  println("--- Raise.run ---")
  val success: String | Int = Raise.run[String, Int] {
    42
  }
  println(s"Success: $success") // 42

  val failure: String | Int = Raise.run[String, Int] {
    Raise.raise("something went wrong")
    42 // never reached
  }
  println(s"Failure: $failure") // something went wrong
  println()

  // --- Raise.either: returns Either[E, A] ---
  println("--- Raise.either ---")
  val right: Either[String, Int] = Raise.either {
    42
  }
  println(s"Right: $right") // Right(42)

  val left: Either[String, Int] = Raise.either {
    Raise.raise("oops")
    42
  }
  println(s"Left: $left") // Left(oops)
  println()

  // --- Raise.fold: the most general handler ---
  // fold(block)(onError)(onSuccess) — handles both paths
  println("--- Raise.fold ---")
  val folded: String = Raise.fold {
    val x = 10
    val y = 0
    if y == 0 then Raise.raise("Division by zero")
    x / y
  }(error => s"Caught error: $error")(result => s"Got result: $result")
  println(s"fold: $folded") // Caught error: Division by zero
  println()

  // --- The `raises` infix type ---
  // Instead of Raise[E] ?=> A, you can write A raises E
  println("--- infix type `raises` ---")
  def divide(a: Int, b: Int): Int raises String =
    if b == 0 then Raise.raise("Cannot divide by zero")
    a / b

  println(s"10 / 2 = ${Raise.either { divide(10, 2) }}") // Right(5)
  println(s"10 / 0 = ${Raise.either { divide(10, 0) }}") // Left(Cannot divide by zero)
  println()

  // --- Lifting Option / Either / Try with .value ---
  // .value unwraps the success case or raises the error
  println("--- Lifting with .value ---")

  // Option: raises None.type on None
  val fromSome: Either[None.type, Int] = Raise.either {
    val opt: Option[Int] = Some(42)
    opt.value
  }
  println(s"Some(42).value: $fromSome") // Right(42)

  val fromNone: Either[None.type, Int] = Raise.either {
    val opt: Option[Int] = None
    opt.value
  }
  println(s"None.value:     $fromNone") // Left(None)

  // Either: raises the Left value
  val fromRight: Either[String, Int] = Raise.either {
    val e: Either[String, Int] = Right(99)
    e.value
  }
  println(s"Right(99).value: $fromRight") // Right(99)

  val fromLeft: Either[String, Int] = Raise.either {
    val e: Either[String, Int] = Left("bad")
    e.value
  }
  println(s"Left(bad).value: $fromLeft") // Left(bad)

  // Try: raises the Throwable on Failure
  val fromTryOk: Either[Throwable, Int] = Raise.either {
    val t: Try[Int] = Try(42)
    t.value
  }
  println(s"Try(42).value:        $fromTryOk") // Right(42)

  val fromTryFail: Either[Throwable, Int] = Raise.either {
    val t: Try[Int] = Try("nope".toInt)
    t.value
  }
  println(s"Try(bad parse).value: $fromTryFail") // Left(NumberFormatException)
  println()

  // --- Raise.ensure: validation ---
  println("--- Raise.ensure ---")
  def validateAge(age: Int): Int raises String =
    Raise.ensure(age >= 0)("Age cannot be negative")
    Raise.ensure(age <= 150)("Age is unrealistic")
    age

  println(s"age 25:  ${Raise.either { validateAge(25) }}")  // Right(25)
  println(s"age -1:  ${Raise.either { validateAge(-1) }}")  // Left(Age cannot be negative)
  println(s"age 200: ${Raise.either { validateAge(200) }}") // Left(Age is unrealistic)
  println()

  // --- Raise.catching: wrapping exceptions into typed errors ---
  println("--- Raise.catching ---")
  val caught: Either[String, Int] = Raise.either {
    Raise.catching {
      "not_a_number".toInt
    }(ex => s"Parse failed: ${ex.getMessage}")
  }
  println(s"catching: $caught") // Left(Parse failed: ...)
  println()

  println("=== End of Lesson 1 ===")
