package com.example.grpc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Production-style gRPC client with a Resilience4j circuit breaker and retry.
 *
 * Circuit breaker states:
 *   CLOSED     – normal operation, calls go through
 *   OPEN       – failure threshold breached; calls fail fast for waitDuration
 *   HALF_OPEN  – probe window; if probes pass, returns to CLOSED, else re-OPENs
 */
public class ResilientHelloClient {

    private static final Logger logger = Logger.getLogger(ResilientHelloClient.class.getName());

    private final ManagedChannel channel;
    private final HelloServiceGrpc.HelloServiceBlockingStub blockingStub;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientHelloClient(String host, int port) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        circuitBreaker = buildCircuitBreaker();
        retry = buildRetry();

        registerStateListener();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String sayHello(String name) {
        // Retry wraps CircuitBreaker: retry only when circuit is CLOSED / HALF_OPEN.
        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        () -> callSayHello(name)));
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            logger.warning("Circuit OPEN — request rejected without calling server.");
            return "CIRCUIT_OPEN";
        } catch (Exception e) {
            logger.warning("Call failed after retries: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String callSayHello(String name) {
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloResponse response = blockingStub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .sayHello(request);
        return response.getMessage();
    }

    private CircuitBreaker buildCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Open after 50 % of calls in the sliding window fail
                .failureRateThreshold(50)
                // Count-based window of 6 calls
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(6)
                // Need at least 4 calls before evaluating failure rate
                .minimumNumberOfCalls(4)
                // Stay OPEN for 10 s before moving to HALF_OPEN
                .waitDurationInOpenState(Duration.ofSeconds(10))
                // Allow 2 probe calls in HALF_OPEN
                .permittedNumberOfCallsInHalfOpenState(2)
                // Treat gRPC status errors as failures
                .recordException(t -> t instanceof StatusRuntimeException)
                .build();

        return CircuitBreakerRegistry.ofDefaults().circuitBreaker("helloService", config);
    }

    private Retry buildRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(300))
                // Do not retry when the circuit is open — fail fast
                .ignoreExceptions(CallNotPermittedException.class)
                .retryExceptions(StatusRuntimeException.class)
                .build();

        return RetryRegistry.ofDefaults().retry("helloService", config);
    }

    private void registerStateListener() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(e -> logger.info(
                        "[CircuitBreaker] " + e.getStateTransition().getFromState()
                        + " → " + e.getStateTransition().getToState()))
                .onCallNotPermitted(e -> logger.warning("[CircuitBreaker] Call rejected (OPEN)"))
                .onError(e -> logger.warning("[CircuitBreaker] Recorded failure: " + e.getThrowable().getMessage()))
                .onSuccess(e -> logger.fine("[CircuitBreaker] Call succeeded in " + e.getElapsedDuration().toMillis() + " ms"));
    }

    // -------------------------------------------------------------------------
    // Demo main — simulates failures to trigger state transitions
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {
        ResilientHelloClient client = new ResilientHelloClient("localhost", 50051);

        System.out.println("=== Resilience4j Circuit Breaker + Retry Demo ===\n");
        System.out.println("Start HelloServer in a separate terminal, then stop it mid-demo to trigger the circuit breaker.\n");

        for (int i = 1; i <= 20; i++) {
            System.out.printf("Call #%02d | state=%-9s | ", i, client.circuitState());
            String result = client.sayHello("World");
            System.out.println("result=" + result);

            Thread.sleep(1_000);
        }

        client.shutdown();
    }
}
