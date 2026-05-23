# Java gRPC Example

A small Java gRPC project that demonstrates:

- a unary RPC
- a server-side streaming RPC
- Java source generation from a Protocol Buffers service definition

## Prerequisites

- Java 17+
- Maven 3.6+

## Project Structure

```
grpc/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── proto/
        │   └── hello.proto          # Service definition
        └── java/com/example/grpc/
            ├── HelloServiceImpl.java # Server-side service implementation
            ├── HelloServer.java      # gRPC server
            └── HelloClient.java      # gRPC client
```

## Service Definition

The `HelloService` in `hello.proto` exposes two RPCs:

| RPC | Type | Description |
|-----|------|-------------|
| `SayHello` | Unary | Returns a single greeting |
| `SayHelloServerStream` | Server Streaming | Streams 5 greetings back |

The generated Java classes are created under `target/generated-sources` during the Maven build.

## Build

```bash
mvn clean package
```

This compiles the `.proto` file and generates Java sources automatically, then packages everything into a fat JAR.

## Run

Start the server in one terminal:

```bash
mvn exec:java -Dexec.mainClass=com.example.grpc.HelloServer
```

Run the client in another terminal:

```bash
mvn exec:java -Dexec.mainClass=com.example.grpc.HelloClient
```

Pass a custom name:

```bash
mvn exec:java -Dexec.mainClass=com.example.grpc.HelloClient -Dexec.args="Alice"
```

You can also run the packaged JAR after `mvn clean package`.

Start the server:

```bash
java -cp target/grpc-java-example-1.0-SNAPSHOT.jar com.example.grpc.HelloServer
```

Run the client:

```bash
java -cp target/grpc-java-example-1.0-SNAPSHOT.jar com.example.grpc.HelloClient
java -cp target/grpc-java-example-1.0-SNAPSHOT.jar com.example.grpc.HelloClient Alice
```

## Expected Output

```
--- Unary RPC ---
Response: Hello, World!

--- Server Streaming RPC ---
Stream response: Hello, World!
Stream response: Hi, World!
Stream response: Hey, World!
Stream response: Greetings, World!
Stream response: Welcome, World!
Server stream completed.
```

---

## Circuit Breaker + Retry (Production POC)

`ResilientHelloClient` wraps every gRPC call with a [Resilience4j](https://resilience4j.readme.io/) circuit breaker and retry policy.

### How it works

```
Request → Retry (max 3 attempts, 300 ms backoff)
              └── CircuitBreaker
                      └── gRPC stub (2 s deadline)
```

| CB State   | Behaviour |
|------------|-----------|
| `CLOSED`   | Calls go through normally |
| `OPEN`     | Calls fail immediately — server not contacted |
| `HALF_OPEN`| 2 probe calls allowed; success → CLOSED, failure → OPEN |

### Circuit breaker config

| Parameter | Value | Meaning |
|-----------|-------|---------|
| `slidingWindowSize` | 6 | Rolling call window |
| `minimumNumberOfCalls` | 4 | Min calls before evaluating |
| `failureRateThreshold` | 50 % | Opens after ≥ 50 % failures |
| `waitDurationInOpenState` | 10 s | How long it stays OPEN |
| `permittedCallsInHalfOpen` | 2 | Probe calls to test recovery |

### Demo

Start the server in one terminal:

```bash
mvn exec:java -Dexec.mainClass=com.example.grpc.HelloServer
```

Run the resilient client demo in another (makes 20 calls, 1 s apart):

```bash
mvn exec:java -Dexec.mainClass=com.example.grpc.ResilientHelloClient
```

**To trigger the circuit breaker:** stop the server mid-demo. After 4+ failures the circuit opens; calls return `CIRCUIT_OPEN` instantly. Restart the server — after 10 s the circuit moves to `HALF_OPEN`, probes pass, and it closes again.

### Example output

```
Call #01 | state=CLOSED    | result=Hello, World!
Call #02 | state=CLOSED    | result=Hello, World!
# (server stopped)
[CircuitBreaker] Recorded failure: UNAVAILABLE: ...
Call #03 | state=CLOSED    | result=ERROR: ...
[CircuitBreaker] CLOSED → OPEN
Call #04 | state=OPEN      | result=CIRCUIT_OPEN    ← no network call made
Call #05 | state=OPEN      | result=CIRCUIT_OPEN
# (10 s pass)
[CircuitBreaker] OPEN → HALF_OPEN
# (server restarted)
Call #06 | state=HALF_OPEN | result=Hello, World!
[CircuitBreaker] HALF_OPEN → CLOSED
Call #07 | state=CLOSED    | result=Hello, World!
```
