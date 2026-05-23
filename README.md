# Java gRPC Example

A simple Java gRPC project demonstrating unary and server-side streaming RPCs.

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

## Build

```bash
mvn clean package
```

This compiles the `.proto` file and generates Java sources automatically, then packages everything into a fat JAR.

## Run

**Start the server** (in one terminal):
```bash
java -cp target/grpc-java-example-1.0-SNAPSHOT.jar com.example.grpc.HelloServer
```

**Run the client** (in another terminal):
```bash
java -cp target/grpc-java-example-1.0-SNAPSHOT.jar com.example.grpc.HelloClient
# or with a custom name:
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
