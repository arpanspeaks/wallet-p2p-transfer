package com.paytm.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletConcurrencyIntegrationTest {
    private static final int WALLET_RACE_REQUESTS = 50;
    private static final int IDEMPOTENCY_RETRIES = 30;
    private static final int CONTENTION_ATTEMPTS = 120;
    private static final int MAX_CLIENT_WORKERS = 50;
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet_test")
            .withUsername("wallet")
            .withPassword("wallet");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    @LocalServerPort
    private int port;

    @Test
    void concurrentWalletCreationReturnsOneWallet() throws Exception {
        String user = "wallet-race-" + UUID.randomUUID();
        List<ApiResponse> responses = concurrently(WALLET_RACE_REQUESTS, () -> request("POST", "/wallets", user, null));

        assertTrue(responses.stream().allMatch(response -> response.status() == 201), responses::toString);
        Set<String> walletIds = responses.stream().map(response -> response.body().path("wallet_id").asText()).collect(java.util.stream.Collectors.toSet());
        assertEquals(1, walletIds.size(), "one user must have exactly one wallet under concurrent creation");
    }

    @Test
    void idempotencyStormAppliesTransferExactlyOnce() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String sender = "sender-" + suffix;
        String recipient = "recipient-" + suffix;
        String from = createWallet(sender);
        String to = createWallet(recipient);
        long senderBefore = balance(from, sender);
        long recipientBefore = balance(to, recipient);
        String key = "storm-" + suffix;
        String body = "{\"from\":\"%s\",\"to\":\"%s\",\"amount_paise\":250,\"idempotency_key\":\"%s\"}".formatted(from, to, key);

        List<ApiResponse> responses = concurrently(IDEMPOTENCY_RETRIES, () -> request("POST", "/transfers", sender, body));

        assertTrue(responses.stream().allMatch(response -> response.status() == 201), responses::toString);
        assertEquals(1, responses.stream().map(response -> response.body().path("transfer_id").asText()).distinct().count());
        assertEquals(senderBefore - 250, balance(from, sender));
        assertEquals(recipientBefore + 250, balance(to, recipient));
    }

    @Test
    void contentionPreservesMoneyAndNeverOverdraws() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String alice = "alice-" + suffix;
        String bob = "bob-" + suffix;
        String carol = "carol-" + suffix;
        String a = createWallet(alice);
        String b = createWallet(bob);
        String c = createWallet(carol);
        long totalBefore = balance(a, alice) + balance(b, bob) + balance(c, carol);

        List<TransferAttempt> attempts = IntStream.range(0, CONTENTION_ATTEMPTS).mapToObj(index -> {
            String[][] routes = {{a, b, alice}, {b, a, bob}, {a, c, alice}, {c, a, carol}};
            String[] route = routes[index % routes.length];
            long amount = index % 5 == 0 ? 250_000 : 5_000;
            return new TransferAttempt(route[0], route[1], route[2], amount, "contention-" + suffix + "-" + index);
        }).toList();

        List<ApiResponse> responses = concurrently(attempts);

        assertTrue(responses.stream().allMatch(response -> response.status() == 201 || response.status() == 422), responses::toString);
        long[] balances = {balance(a, alice), balance(b, bob), balance(c, carol)};
        assertEquals(totalBefore, balances[0] + balances[1] + balances[2], "transfers must conserve money");
        assertTrue(java.util.Arrays.stream(balances).allMatch(value -> value >= 0), "no wallet can have a negative balance");
        assertTrue(responses.stream().anyMatch(response -> response.status() == 422), "overdraft attempts must be declined");
    }

    private String createWallet(String token) throws Exception {
        ApiResponse response = request("POST", "/wallets", token, null);
        assertEquals(201, response.status(), response::toString);
        return response.body().path("wallet_id").asText();
    }

    private long balance(String walletId, String token) throws Exception {
        ApiResponse response = request("GET", "/wallets/" + walletId, token, null);
        assertEquals(200, response.status(), response::toString);
        return response.body().path("balance_paise").asLong();
    }

    private ApiResponse request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(90));
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.method(method, HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json");
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), json.readTree(response.body()));
    }

    private List<ApiResponse> concurrently(int count, Callable<ApiResponse> operation) throws Exception {
        List<Callable<ApiResponse>> operations = IntStream.range(0, count).mapToObj(ignored -> operation).toList();
        return runConcurrently(operations);
    }

    private List<ApiResponse> concurrently(List<TransferAttempt> attempts) throws Exception {
        List<Callable<ApiResponse>> operations = attempts.stream().<Callable<ApiResponse>>map(attempt -> () -> request("POST", "/transfers", attempt.token(),
                "{\"from\":\"%s\",\"to\":\"%s\",\"amount_paise\":%d,\"idempotency_key\":\"%s\"}".formatted(
                        attempt.from(), attempt.to(), attempt.amount(), attempt.idempotencyKey()))).toList();
        return runConcurrently(operations);
    }

    private List<ApiResponse> runConcurrently(List<Callable<ApiResponse>> operations) throws Exception {
        int parallelism = Math.min(MAX_CLIENT_WORKERS, operations.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CountDownLatch ready = new CountDownLatch(parallelism);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int index = 0; index < operations.size(); index++) {
                Callable<ApiResponse> operation = operations.get(index);
                boolean waitsForStart = index < parallelism;
                futures.add(executor.submit(() -> {
                    if (waitsForStart) { ready.countDown(); start.await(); }
                    return operation.call();
                }));
            }
            assertTrue(ready.await(15, java.util.concurrent.TimeUnit.SECONDS), "workers did not become ready");
            start.countDown();
            List<ApiResponse> results = new ArrayList<>();
            for (Future<ApiResponse> future : futures) results.add(future.get());
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record ApiResponse(int status, JsonNode body) { }
    private record TransferAttempt(String from, String to, String token, long amount, String idempotencyKey) { }
}
