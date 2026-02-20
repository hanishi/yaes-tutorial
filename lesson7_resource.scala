// Lesson 7: Resource Management
//
// The Resource effect ensures cleanup actions run even when errors occur.
// Resources are released in LIFO (last-in, first-out) order.

import in.rcard.yaes.*
import java.io.Closeable

@main def lesson7(): Unit =
  println("=== Lesson 7: Resource Management ===")
  println()

  // --- Resource.install: custom acquire/release ---
  // install(acquire)(release) runs acquire, registers release for later
  println("--- Resource.install ---")
  Resource.run {
    val handle = Resource.install {
      println("  Acquiring resource A")
      "ResourceA"
    } { name =>
      println(s"  Releasing $name")
    }
    println(s"  Using $handle")
  }
  // Output shows: Acquire, Use, Release
  println()

  // --- LIFO release order ---
  // Resources acquired later are released first
  println("--- LIFO Release Order ---")
  Resource.run {
    val a = Resource.install {
      println("  Acquiring A")
      "A"
    } { name => println(s"  Releasing $name") }

    val b = Resource.install {
      println("  Acquiring B")
      "B"
    } { name => println(s"  Releasing $name") }

    val c = Resource.install {
      println("  Acquiring C")
      "C"
    } { name => println(s"  Releasing $name") }

    println(s"  Using $a, $b, $c")
  }
  // Release order: C, B, A (reverse of acquisition)
  println()

  // --- Resource.acquire: auto-close Closeable objects ---
  println("--- Resource.acquire (Closeable) ---")

  // A simple Closeable for demonstration
  class MyConnection(val name: String) extends Closeable:
    println(s"  Connection '$name' opened")
    def query(sql: String): String = s"Result of '$sql' from $name"
    def close(): Unit = println(s"  Connection '$name' closed")

  Resource.run {
    val conn = Resource.acquire(MyConnection("db1"))
    val result = conn.query("SELECT * FROM users")
    println(s"  $result")
  }
  // Connection is automatically closed when the block exits
  println()

  // --- Resource.ensuring: run a finalizer without a resource ---
  println("--- Resource.ensuring ---")
  Resource.run {
    Resource.ensuring {
      println("  Finalizer ran!")
    }
    println("  Doing some work...")
  }
  println()

  // --- Resource + Raise: cleanup happens even on error ---
  println("--- Resource + Raise interaction ---")
  val result: Either[String, String] = Raise.either {
    Resource.run {
      val r = Resource.install {
        println("  Acquiring precious resource")
        "precious"
      } { name => println(s"  Releasing $name (even after error!)") }

      println(s"  Using $r...")
      Raise.raise("Something went wrong!")
      s"Success with $r" // never reached
    }
  }
  println(s"Result: $result")
  println()

  // --- Multiple resources with error ---
  println("--- Multiple resources + error ---")
  val result2: Either[String, String] = Raise.either {
    Resource.run {
      val db = Resource.install {
        println("  Opening database")
        "db"
      } { name => println(s"  Closing $name") }

      val file = Resource.install {
        println("  Opening file")
        "file"
      } { name => println(s"  Closing $name") }

      val cache = Resource.install {
        println("  Initializing cache")
        "cache"
      } { name => println(s"  Clearing $name") }

      println(s"  Processing with $db, $file, $cache...")
      Raise.raise("Unexpected failure!")
      "done"
    }
  }
  println(s"Result: $result2")
  // All three resources are released in reverse order, even with an error
  println()

  println("=== End of Lesson 7 ===")
