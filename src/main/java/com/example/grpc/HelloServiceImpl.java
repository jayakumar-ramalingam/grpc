package com.example.grpc;

import io.grpc.stub.StreamObserver;

public class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

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
                Thread.currentThread().interrupt();
                responseObserver.onCompleted();
                return;
            }
        }
        responseObserver.onCompleted();
    }

    private String displayName(HelloRequest request) {
        String name = request.getName().trim();
        return name.isEmpty() ? "friend" : name;
    }
}
