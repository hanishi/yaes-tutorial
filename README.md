# YAES Tutorial

A hands-on tutorial for learning [yaes](https://github.com/rcardin/yaes) (Yet Another Effect System) — an algebraic effect system for Scala 3 that uses context parameters for direct-style effect management.

> **Why this tutorial exists**
>
> There is virtually no Japanese-language documentation on algebraic effect systems for Scala. Most resources on Cats Effect and ZIO are in English, and yaes — a newcomer that embraces Scala 3's native features over monadic abstractions — has none at all. This tutorial fills that gap with a hands-on, lesson-by-lesson introduction written entirely in Japanese. We believe yaes's lightweight, non-framework approach will resonate with Japanese developers who value clean, pragmatic code over heavy abstractions.

**[日本語版はこちら / Japanese version](ja/README.md)**

## What is an Effect System?

### "Effects"

Pure functions just compute outputs from inputs, but real programs involve **side effects**:

- Printing to the console
- Reading/writing files
- Throwing exceptions
- Mutating state
- Generating random numbers
- Concurrency

An effect system **tracks and controls these side effects through the type system**.

### Traditional Approaches and Their Trade-offs

Scala has two major traditions for managing effects.

**1. Monadic (Cats Effect / ZIO)**

```scala
// ZIO example
def program: ZIO[Console & Random, IOException, Unit] =
  for
    n    <- Random.nextInt
    _    <- Console.printLine(s"Random: $n")
  yield ()
```

The monadic style composes operations with `for`/`yield` (flatMap chains). It's type-safe and tracks effects well, but comes with costs:

- **`for`/`yield` required** — you can't use normal `if`/`while`/`try`; you need special combinators
- **Steep learning curve** — monad transformers, fibers, scopes, and other framework-specific concepts
- **Ecosystem lock-in** — your entire codebase depends on the specific effect type

**2. Direct style (yaes / Kyo etc.)**

```scala
// yaes example
def program(using Output, Random): Unit =
  val n = Random.nextInt
  Output.printLn(s"Random: $n")
```

Direct style lets you write effects as **ordinary Scala code**. No `for`/`yield` needed — `if`/`while`/`try` just work.

### Why yaes?

| Feature | yaes | Cats Effect | ZIO |
|---------|------|-------------|-----|
| Style | Direct | Monadic | Monadic |
| Effect declaration | `using` parameters | Type parameter `F[_]` | `ZIO[R, E, A]` |
| Control flow | `if`/`while`/`try` | Special combinators | Special combinators |
| Learning curve | Low | High | High |
| Scala 3 usage | Full use of context functions | Scala 2 heritage | Scala 2 heritage |
| Dependencies | None (stdlib only) | cats-core | zio |

yaes's key insight is **using Scala 3 context parameters (`using`) as the effect mechanism itself**. No special wrapper types or monads needed — ordinary functions become effectful functions.

### Exceptions vs Either vs Raise — The Real Difference

If you're new to effect systems, you might be wondering:

> "Why not just throw exceptions?" "How is this different from Either?"

Fair questions. Let's compare all three with the same example.

**Exceptions — easy to write, but errors are invisible in the types**

```scala
def divide(a: Int, b: Int): Int =
  if b == 0 then throw new ArithmeticException("division by zero")
  a / b

def calculate(x: Int): Int =
  divide(divide(x, 2), 3)  // concise, but the type doesn't show it can fail
```

Exceptions are concise and propagate automatically. But `calculate`'s signature `Int => Int` doesn't tell you it can fail. The compiler doesn't force you to handle errors.

**Either — type-safe, but syntactically heavy**

```scala
def divide(a: Int, b: Int): Either[String, Int] =
  if b == 0 then Left("division by zero") else Right(a / b)

def calculate(x: Int): Either[String, Int] =
  divide(x, 2).flatMap(r => divide(r, 3))  // flatMap chains required
```

`Either` expresses errors in the types. The compiler forces you to handle them. But every computation must be chained with `flatMap`. As logic grows, nesting deepens and code looks nothing like normal Scala.

**Raise — the ergonomics of exceptions + the type safety of Either**

```scala
def divide(a: Int, b: Int): Int raises String =
  if b == 0 then Raise.raise("division by zero")
  a / b

def calculate(x: Int): Int raises String =
  divide(divide(x, 2), 3)  // concise AND errors are visible in the type
```

`Raise` gives you **both**:

- Write normal function calls like exceptions — no `flatMap` needed
- Errors propagate automatically — no manual threading
- `raises String` appears in the type signature — the compiler enforces error handling
- Uses `boundary`/`break` internally — faster than exceptions (no stack trace generation)

In short, **Raise gives you "exception ergonomics" and "Either type safety" at the same time**. It's not "exceptions or Either" — it's both.

| | Ergonomics | Error propagation | Type safety | Performance |
|---|---|---|---|---|
| **Exceptions** | Concise | Automatic | None | Stack trace cost |
| **Either** | Requires flatMap | Manual (flatMap) | Yes | Fast |
| **Raise** | Concise | Automatic | Yes | Fast (boundary/break) |

### For Java developers: Compared to Checked Exceptions

If you're coming from Java, you might recall checked exceptions:

```java
// Java checked exceptions — errors appear in the method signature
int divide(int a, int b) throws ArithmeticException {
    if (b == 0) throw new ArithmeticException("division by zero");
    return a / b;
}
```

At first glance, this is the same idea as `Raise` — make errors visible in the type and let the compiler enforce handling. But Java's checked exceptions failed in practice. Why?

| | Checked Exceptions (Java) | Raise (yaes) |
|---|---|---|
| **Error type composition** | Requires a common superclass; everyone ends up with `throws Exception` | `Raise[A]` and `Raise[B]` declared independently |
| **Propagation boilerplate** | Every method in the chain must declare `throws` | `using Raise[E]` propagates implicitly via context parameters |
| **Lambda compatibility** | `stream.map(x -> mightThrow(x))` won't compile | Works naturally with lambdas |
| **Error values** | Only exception objects | Any type — strings, enums, ADTs |
| **Performance** | Stack trace generation cost | boundary/break jump (no stack trace) |

Scala intentionally dropped checked exceptions from Java. `Raise` brings back the **part that was right** (compiler-enforced error tracking) without the **parts that were wrong** (boilerplate, no lambda support, no composition).

```scala
// Java's idea, done right in Scala 3
def divide(a: Int, b: Int): Int raises String =
  if b == 0 then Raise.raise("division by zero")
  a / b
```

## How yaes Works

### Core Structure

The core of yaes is simple:

```scala
// A wrapper representing the "capability" to use an effect
class Yaes[+F](val unsafe: F)

// Each effect is defined as a type alias
type Raise[E] = Yaes[Raise.Unsafe[E]]
type State[S] = Yaes[State.Unsafe[S]]
type Output   = Yaes[Output.Unsafe]
type Random   = Yaes[Random.Unsafe]
// ...
```

`Yaes[F]` is a marker meaning "the capability `F` is available." Each effect has an internal `Unsafe` trait that defines the actual operations.

### Declaring and Using Effects

Functions declare their required effects as `using` parameters:

```scala
// This function requires "the ability to fail" and "randomness"
def rollOrFail()(using Random, Raise[String]): Int =
  val n = Random.nextInt
  if n % 2 == 0 then Raise.raise("Rolled an even number!")
  n
```

`using` is Scala 3's context parameter mechanism. If the caller doesn't provide the effect, the compiler raises an error — **effect tracking is enforced at compile time**.

### Running Effects with Handlers

Effectful functions are executed by wrapping them in handlers (`run`):

```scala
// Nest handlers to provide all effects
val result: Either[String, Int] = Random.run {
  Raise.either {
    rollOrFail()
  }
}
```

Handlers serve three roles:
1. **Provide the implementation** — `Random.run` injects a real random number generator
2. **Transform the result** — `Raise.either` converts errors into `Either`
3. **Delimit the scope** — the effect is unavailable outside the handler block

### How `Raise` Works Internally

`Raise` is yaes's core effect. It uses the boundary/break mechanism for short-circuiting without exception overhead:

```
Raise.either { ... Raise.raise("error") ... }

1. either sets up a boundary
2. raise performs a break (jumps back to the boundary)
3. either wraps the error in Left and returns
```

Compared to traditional `throw`/`catch`:
- **Type-safe** — the type tells you exactly what errors can occur
- **No stack traces** — boundary/break doesn't generate stack traces, making it fast
- **Composable** — freely combine multiple error types

### Composing Effects

Multiple effects compose naturally by listing `using` parameters:

```scala
// A function requiring three effects
def game()(using Random, State[Int], Output): Unit =
  val coin = Random.nextBoolean
  if coin then State.update[Int](_ + 1)
  Output.printLn(s"Result: ${if coin then "heads" else "tails"}")

// Nest handlers to provide all effects
Output.run {
  Random.run {
    State.run(0) {
      game()
    }
  }
}
```

Unlike monadic style, the code structure doesn't change as you add more effects. You just add more `using` parameters.

## Effect Catalog

### Raise — Typed Errors

Short-circuit computations with typed errors. Direct-style `Either`.

```scala
def divide(a: Int, b: Int): Int raises String =
  if b == 0 then Raise.raise("division by zero")
  a / b

Raise.either { divide(10, 0) } // Left("division by zero")
```

### State — State Management

Controlled mutable state.

```scala
val (finalState, result) = State.run(0) {
  State.update[Int](_ + 1)
  State.update[Int](_ * 10)
  State.get[Int]  // 10
}
// finalState = 10, result = 10
```

### Output / Input — Console I/O

Console I/O as effects. Swappable with mock implementations for testing.

```scala
Output.run {
  Output.printLn("Hello")
}
```

### Random / Clock — Randomness and Time

Random number generation and time access as effects.

```scala
Random.run {
  val n = Random.nextInt
}
Clock.run {
  val now = Clock.now  // Instant
}
```

### Resource — Resource Management

Guarantees resource cleanup. Release actions run even when errors occur.

```scala
Resource.run {
  val conn = Resource.acquire(new MyConnection())
  conn.query("SELECT 1")
} // automatically closed when the block exits
```

### Async — Structured Concurrency (Java 24+)

Structured concurrency using Java 24's `StructuredTaskScope`.

```scala
Async.run {
  val (a, b) = Async.par(
    { heavyComputation1() },
    { heavyComputation2() }
  )
  // Waits for both. If one fails, the other is cancelled.
}
```

## Testability — The Biggest Practical Benefit

The most practical reason to use an effect system is **testability**.

Effects are injected via `using` parameters, so you can swap real implementations with mocks at test time. This is language-level dependency injection — no DI container needed.

### Creating Mocks

Just implement the `Unsafe` trait and wrap it in `Yaes`:

```scala
// A Random mock that always returns the same value
def fixedRandom(value: Int): Random =
  new Yaes(new Random.Unsafe {
    def nextInt(): Int = value
    def nextBoolean(): Boolean = value % 2 == 0
    def nextDouble(): Double = value.toDouble
    def nextLong(): Long = value.toLong
  })

// An Output mock that captures output to a buffer
def bufferedOutput(): (Output, () => List[String]) =
  val buffer = scala.collection.mutable.ListBuffer.empty[String]
  val output: Output = new Yaes(new Output.Unsafe {
    def print(text: String): Unit = buffer += text
    def printLn(text: String): Unit = buffer += text
    def printErr(text: String): Unit = ()
    def printErrLn(text: String): Unit = ()
  })
  (output, () => buffer.toList)
```

### Writing Tests

```scala
class MyTests extends munit.FunSuite:

  // Raise: use either to verify error paths
  test("rejects empty username") {
    val result = Raise.either { validateUsername("") }
    assertEquals(result, Left("Username is required"))
  }

  // Random: inject a mock for deterministic tests
  test("roll 6 is a jackpot") {
    given Random = fixedRandom(5) // (5 % 6).abs + 1 = 6
    val result = Raise.either { luckyMessage() }
    assertEquals(result, Right("Jackpot!"))
  }

  // Output: capture output in a buffer
  test("outputs correct message") {
    val (testOutput, getLines) = bufferedOutput()
    given Output = testOutput
    greetUser("Alice")
    assert(getLines().head.contains("Alice"))
  }

  // State: assert on both final state and result
  test("increments correctly") {
    val (finalState, result) = State.run(0) { incrementN(5) }
    assertEquals(finalState, 5)
  }
```

No framework-specific mock libraries (Mockito etc.) needed. The effect system natively supports mocking.

## Prerequisites

- [scala-cli](https://scala-cli.virtuslab.org/) installed
- Java 21+ (lessons 1–7)
- Java 24+ (lesson 8: structured concurrency)

## Lessons

| Lesson  | Topic |
|---------|-------|
| lesson1 | **Typed Error Handling with Raise** — `raise`, `run`, `fold`, `either`, `raises` type, `.value`, `ensure`, `catching` |
| lesson2 | **Advanced Raise Patterns** — `recover`, `withDefault`, `withError`, `accumulate`, `traced` |
| lesson3 | **Stateful Computation with State** — `run`, `get`, `set`, `update`, `use` |
| lesson4 | **Console I/O** — `Output.printLn`, `Input.readLn` (interactive) |
| lesson5 | **Random and Clock** — `Random`, `Clock` |
| lesson6 | **Composing Multiple Effects** — multi-effect functions, coin flip game |
| lesson7 | **Resource Management** — `Resource.install`, `acquire`, LIFO release order |
| lesson8 | **Structured Concurrency** — `Async.fork`, `par`, `race`, `timeout` (requires Java 24+) |
| lesson9 | **Testability** — creating mocks, testing effectful code |

## Running

```bash
# Show lesson index
scala-cli run . --main-class index

# Run individual lessons
scala-cli run . --main-class lesson1
scala-cli run . --main-class lesson2
# ...
scala-cli run . --main-class lesson9

# Run tests
scala-cli test .
```

## Project Structure

```
project.scala                  # Build config (Scala 3.8.1 + yaes-core + munit)
main.scala                     # Lesson index
lesson1_raise.scala            # Raise basics
lesson2_raise_advanced.scala   # Raise advanced
lesson3_state.scala            # State
lesson4_io.scala               # Output / Input
lesson5_random_clock.scala     # Random / Clock
lesson6_composing.scala        # Effect composition
lesson7_resource.scala         # Resource
lesson8_async.scala            # Async (requires Java 24+)
lesson9_testing.scala          # Testability
tests/ExampleTests.test.scala  # munit tests
ja/                            # Japanese version (日本語版)
```

## Links

- [yaes GitHub](https://github.com/rcardin/yaes) — Source code and documentation
- [Scala 3 Context Functions](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html) — The language feature yaes is built on
- [JEP 505: Structured Concurrency](https://openjdk.org/jeps/505) — The Java 24 feature behind `Async`
