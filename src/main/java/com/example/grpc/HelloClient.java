package com.example.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HelloClient {

    private static final String HOST = "localhost";
    private static final int PORT = 50051;

    private final ManagedChannel channel;
    private final HelloServiceGrpc.HelloServiceBlockingStub blockingStub;
    private final HelloServiceGrpc.HelloServiceStub asyncStub;

    public HelloClient() {
        channel = ManagedChannelBuilder.forAddress(HOST, PORT)
                .usePlaintext()
                .build();
        blockingStub = HelloServiceGrpc.newBlockingStub(channel);
        asyncStub = HelloServiceGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void sayHello(String name) {
        System.out.println("\n--- Unary RPC ---");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloResponse response = blockingStub.sayHello(request);
        System.out.println("Response: " + response.getMessage());
    }

    public void sayHelloServerStream(String name) throws InterruptedException {
        System.out.println("\n--- Server Streaming RPC ---");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        CountDownLatch latch = new CountDownLatch(1);

        asyncStub.sayHelloServerStream(request, new StreamObserver<HelloResponse>() {
            @Override
            public void onNext(HelloResponse response) {
                System.out.println("Stream response: " + response.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Stream error: " + t.getMessage());
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Server stream completed.");
                latch.countDown();
            }
        });

        latch.await(30, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        HelloClient client = new HelloClient();
        try {
            String name = args.length > 0 ? args[0] : "World";
            client.sayHello(name);
            client.sayHelloServerStream(name);
        } finally {
            client.shutdown();
        }
    }
}
