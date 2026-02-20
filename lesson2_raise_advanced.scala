// Lesson 2: Advanced Raise Patterns
//
// Building on Lesson 1, we explore recovery, error transformation,
// error accumulation, and tracing.

import in.rcard.yaes.*

@main def lesson2(): Unit =
  println("=== Lesson 2: Advanced Raise Patterns ===")
  println()

  // --- Raise.recover: handle errors and return a fallback ---
  println("--- Raise.recover ---")
  val recovered: Int = Raise.recover {
    Raise.raise("something broke")
    42
  }(error => -1)
  println(s"recovered: $recovered") // -1

  val notRecovered: Int = Raise.recover {
    42 // no error raised
  }(error => -1)
  println(s"no error:  $notRecovered") // 42
  println()

  // --- Raise.withDefault: simpler recovery with a default value ---
  println("--- Raise.withDefault ---")
  val defaulted: Int = Raise.withDefault(0) {
    Raise.raise("error")
    42
  }
  println(s"withDefault (error): $defaulted") // 0

  val noDefault: Int = Raise.withDefault(0) {
    42
  }
  println(s"withDefault (ok):    $noDefault") // 42
  println()

  // --- Raise.withError: transform error types ---
  // Useful when composing functions with different error types
  println("--- Raise.withError ---")
  sealed trait AppError
  case class ValidationError(msg: String) extends AppError
  case class ParseError(msg: String) extends AppError

  def parseInt(s: String): Int raises String =
    Raise.catching(s.toInt)(ex => s"Not a number: $s")

  // Transform String errors from parseInt into AppError
  val transformed: Either[AppError, Int] = Raise.either {
    Raise.withError[AppError, String, Int](e => ParseError(e)) {
      parseInt("abc")
    }
  }
  println(s"withError: $transformed") // Left(ParseError(Not a number: abc))

  val transformedOk: Either[AppError, Int] = Raise.either {
    Raise.withError[AppError, String, Int](e => ParseError(e)) {
      parseInt("42")
    }
  }
  println(s"withError (ok): $transformedOk") // Right(42)
  println()

  // --- Raise.accumulate: collect ALL errors instead of short-circuiting ---
  // Normally Raise stops at the first error. accumulate gathers them all.
  println("--- Raise.accumulate ---")

  case class FormData(name: String, age: Int, email: String)

  // Validation with accumulation — all three fields validated independently
  val allErrors: Either[List[String], FormData] = Raise.either {
    Raise.accumulate[List, String, FormData] {
      import scala.language.implicitConversions
      val name: Raise.LazyValue[String] = Raise.accumulating {
        Raise.ensure("".nonEmpty)("Name is required")
        ""
      }
      val age: Raise.LazyValue[Int] = Raise.accumulating {
        Raise.ensure(-5 >= 0)("Age must be non-negative")
        -5
      }
      val email: Raise.LazyValue[String] = Raise.accumulating {
        val e = "not-an-email"
        Raise.ensure(e.contains("@"))("Email must contain @")
        e
      }
      // When we use these LazyValues, accumulated errors are raised
      FormData(name, age, email)
    }
  }
  println(s"All errors: $allErrors")
  // Left(List(Name is required, Age must be non-negative, Email must contain @))

  // Successful accumulation — no errors
  val allOk: Either[List[String], FormData] = Raise.either {
    Raise.accumulate[List, String, FormData] {
      import scala.language.implicitConversions
      val name: Raise.LazyValue[String] = Raise.accumulating {
        val n = "Alice"
        Raise.ensure(n.nonEmpty)("Name is required")
        n
      }
      val age: Raise.LazyValue[Int] = Raise.accumulating {
        val a = 30
        Raise.ensure(a >= 0)("Age must be non-negative")
        a
      }
      val email: Raise.LazyValue[String] = Raise.accumulating {
        val e = "alice@example.com"
        Raise.ensure(e.contains("@"))("Email must contain @")
        e
      }
      FormData(name, age, email)
    }
  }
  println(s"All ok:     $allOk")
  // Right(FormData(Alice,30,alice@example.com))
  println()

  // --- Raise.traced: adds stack trace information to errors ---
  // By default, Raise errors carry no stack trace (boundary/break).
  // traced wraps them so you get a stack trace printed on error.
  println("--- Raise.traced ---")
  val traced: Either[String, Int] = Raise.either {
    Raise.traced {
      Raise.raise("traced error")
      42
    }
  }
  println(s"traced result: $traced") // Left(traced error)
  // (A stack trace was printed above by the default TraceWith handler)
  println()

  println("=== End of Lesson 2 ===")
