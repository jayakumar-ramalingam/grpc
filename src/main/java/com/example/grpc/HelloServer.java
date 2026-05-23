package com.example.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HelloServer {

    private static final int PORT = 50051;
    private static final Logger LOGGER = Logger.getLogger(HelloServer.class.getName());
    private Server server;

    public void start() throws IOException {
        server = ServerBuilder.forPort(PORT)
                .addService(new HelloServiceImpl())
                .build()
                .start();
        LOGGER.info("Server started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down gRPC server...");
            try {
                HelloServer.this.stop();
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Interrupted while shutting down server", e);
                Thread.currentThread().interrupt();
            }
            LOGGER.info("Server shut down.");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown();
            if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        HelloServer server = new HelloServer();
        server.start();
        server.blockUntilShutdown();
    }
}
