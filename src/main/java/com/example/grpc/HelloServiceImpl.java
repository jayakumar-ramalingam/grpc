package com.example.grpc;

import io.grpc.stub.StreamObserver;

public class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {
    // Reused by the server-streaming RPC to send a sequence of responses.
    private static final String[] GREETINGS = {"Hello", "Hi", "Hey", "Greetings", "Welcome"};

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        String message = "Hello, " + displayName(request) + "!";
        HelloResponse response = HelloResponse.newBuilder()
                .setMessage(message)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void sayHelloServerStream(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        String name = displayName(request);

        for (String greeting : GREETINGS) {
            HelloResponse response = HelloResponse.newBuilder()
                    .setMessage(greeting + ", " + name + "!")
                    .build();
            responseObserver.onNext(response);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Restore the interrupt flag and stop sending responses when the call is interrupted.
                Thread.currentThread().interrupt();
                responseObserver.onCompleted();
                return;
            }
        }
        responseObserver.onCompleted();
    }

    private String displayName(HelloRequest request) {
        String name = request.getName().trim();
        // Keep empty or whitespace-only requests friendly and usable.
        return name.isEmpty() ? "friend" : name;
    }
}
