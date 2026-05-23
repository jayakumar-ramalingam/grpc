package com.example.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HelloClient {

    private static final String HOST = "localhost";
    private static final int PORT = 50051;
    private static final int RPC_DEADLINE_SECONDS = 5;
    private static final Logger LOGGER = Logger.getLogger(HelloClient.class.getName());

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
        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow();
        }
    }

    public void sayHello(String name) {
        System.out.println("\n--- Unary RPC ---");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        try {
            HelloResponse response = blockingStub
                    .withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .sayHello(request);
            System.out.println("Response: " + response.getMessage());
        } catch (StatusRuntimeException e) {
            LOGGER.log(Level.WARNING, "Unary RPC failed: {0}", e.getStatus());
        }
    }

    public void sayHelloServerStream(String name) throws InterruptedException {
        System.out.println("\n--- Server Streaming RPC ---");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        CountDownLatch latch = new CountDownLatch(1);

        asyncStub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .sayHelloServerStream(request, new StreamObserver<HelloResponse>() {
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

        if (!latch.await(30, TimeUnit.SECONDS)) {
            LOGGER.warning("Server streaming RPC timed out.");
        }
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
