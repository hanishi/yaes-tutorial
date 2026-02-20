// Lesson 8: Structured Concurrency with Async
//
// NOTE: This lesson requires Java 24+ (for virtual threads and StructuredTaskScope).
// If you're on an earlier JVM, lessons 1-7 still work fine.
//
// The Async effect provides structured concurrency: all forked fibers are
// guaranteed to complete (or be cancelled) before the parent scope exits.

import in.rcard.yaes.*
import scala.concurrent.duration.*

@main def lesson8(): Unit =
  println("=== Lesson 8: Structured Concurrency with Async ===")
  println()

  // --- Async.run + Async.fork: basic fiber creation ---
  println("--- Async.fork ---")
  Async.run {
    val fiber1 = Async.fork("worker-1") {
      println(s"  [${Thread.currentThread().getName}] Working on task 1...")
      Async.delay(100.millis)
      "Result 1"
    }

    val fiber2 = Async.fork("worker-2") {
      println(s"  [${Thread.currentThread().getName}] Working on task 2...")
      Async.delay(150.millis)
      "Result 2"
    }

    // .value unwraps the fiber result (or raises Cancelled if cancelled)
    val r1: String = Raise.recover(fiber1.value)(_ => "cancelled")
    val r2: String = Raise.recover(fiber2.value)(_ => "cancelled")
    println(s"  fiber1: $r1, fiber2: $r2")
  }
  println()

  // --- Async.par: run two tasks in parallel, wait for both ---
  println("--- Async.par ---")
  Async.run {
    val (a, b) = Async.par(
      {
        println(s"  [${Thread.currentThread().getName}] Computing A...")
        Async.delay(100.millis)
        42
      },
      {
        println(s"  [${Thread.currentThread().getName}] Computing B...")
        Async.delay(200.millis)
        "hello"
      }
    )
    println(s"  par results: ($a, $b)") // (42, hello)
  }
  println()

  // --- Async.race: run two tasks, keep the first result, cancel the other ---
  println("--- Async.race ---")
  Async.run {
    val winner = Async.race(
      {
        println(s"  [${Thread.currentThread().getName}] Fast task...")
        Async.delay(50.millis)
        "fast"
      },
      {
        println(s"  [${Thread.currentThread().getName}] Slow task...")
        Async.delay(500.millis)
        "slow"
      }
    )
    println(s"  Race winner: $winner") // fast (usually)
  }
  println()

  // --- Async.timeout: fail if a task takes too long ---
  println("--- Async.timeout ---")
  Async.run {
    // Successful case: completes within timeout
    val ok: Either[Async.TimedOut, String] = Raise.either {
      Async.timeout(1.second) {
        Async.delay(100.millis)
        "completed in time"
      }
    }
    println(s"  Within timeout: $ok") // Right(completed in time)

    // Timeout case: takes too long
    val timedOut: Either[Async.TimedOut, String] = Raise.either {
      Async.timeout(100.millis) {
        Async.delay(2.seconds)
        "this won't complete"
      }
    }
    println(s"  Exceeded timeout: $timedOut") // Left(TimedOut)
  }
  println()

  // --- Combining Async with other effects ---
  println("--- Async + other effects ---")
  Output.run {
    Async.run {
      Output.printLn("Starting concurrent computation...")

      val (sum, product) = Async.par(
        {
          val result = (1 to 100).sum
          result
        },
        {
          val result = (1 to 10).map(_.toLong).product
          result
        }
      )

      Output.printLn(s"Sum 1..100 = $sum")
      Output.printLn(s"Product 1..10 = $product")
    }
  }
  println()

  println("=== End of Lesson 8 ===")
