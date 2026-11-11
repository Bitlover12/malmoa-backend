package com.aac.ieojwo;

import com.aac.ieojwo.device.domain.AacDevice;
import com.aac.ieojwo.device.domain.DeviceType;
import com.aac.ieojwo.device.repository.AacDeviceRepository;
import com.aac.ieojwo.device.service.DeviceAuthService;
import com.aac.ieojwo.user.domain.AacUser;
import com.aac.ieojwo.user.domain.GridSize;
import com.aac.ieojwo.user.domain.UserMode;
import com.aac.ieojwo.user.repository.AacUserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false",
                "logging.level.root=WARN"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceTrafficLoadIT {
    private static final String HEARTBEAT = "heartbeat";
    private static final String LOCATION = "location";
    private static final String SENSOR = "sensor";
    private static final String LOCATION_BODY =
            "{\"latitude\":37.6026,\"longitude\":126.9553,\"accuracyMeters\":15}";
    private static final String SENSOR_BODY =
            "{\"type\":\"HEART_RATE\",\"numericValue\":80}";
    private static final long HEARTBEAT_INTERVAL_MS = 60_000;
    private static final long LOCATION_INTERVAL_MS = 10_000;
    private static final long SENSOR_INTERVAL_MS = 5_000;

    @LocalServerPort
    private int port;

    @Autowired
    private AacUserRepository users;

    @Autowired
    private AacDeviceRepository devices;

    @Autowired
    private DeviceAuthService deviceAuth;

    @Autowired
    private MeterRegistry meters;

    @Value("${load.users:1,10,100,1000}")
    private String configuredUsers;

    @Value("${load.duration.seconds:30}")
    private int durationSeconds;

    @Value("${load.request.timeout.seconds:10}")
    private int requestTimeoutSeconds;

    @Value("${load.report.dir:target/load-test-results}")
    private String reportDirectory;

    private final List<String> tokens = new ArrayList<>();
    private HttpClient client;

    @BeforeAll
    void prepare() {
        List<Integer> stages = stages();
        int maximumUsers = stages.stream().max(Integer::compareTo).orElseThrow();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        for (int index = 0; index < maximumUsers; index++) {
            AacUser user = users.save(AacUser.create(
                    "load-user-" + index, UserMode.SIMPLE, GridSize.GRID_3X3));
            AacDevice device = devices.save(AacDevice.create(
                    user, "load-device-" + index, "Load Device " + index,
                    DeviceType.TABLET, Instant.now()));
            tokens.add(deviceAuth.issue(device).raw());
        }
    }

    @Test
    void deviceTrafficLoadTest() throws Exception {
        runStage(1, 3, false);

        List<StageResult> results = new ArrayList<>();
        for (int users : stages()) {
            StageResult result = runStage(users, durationSeconds, true);
            results.add(result);
            System.out.println(result.consoleLine());
        }

        Path report = writeReport(results);
        System.out.println("Load-test report: " + report.toAbsolutePath());

        for (StageResult result : results) {
            assertThat(result.errorRate())
                    .as("error rate for %s users", result.users())
                    .isLessThan(0.01);
        }
    }

    private StageResult runStage(int userCount, int seconds, boolean collectMetrics) throws Exception {
        StageRecorder recorder = new StageRecorder();
        ResourceSampler sampler = new ResourceSampler(meters);
        AtomicBoolean sampling = new AtomicBoolean(false);
        Thread samplerThread = null;

        AtomicLong stageStarted = new AtomicLong();
        AtomicLong stageEnd = new AtomicLong();
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < userCount; index++) {
                int virtualUser = index;
                futures.add(executor.submit(() -> runVirtualUser(
                        virtualUser, tokens.get(virtualUser), stageEnd, ready, start, recorder)));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            if (collectMetrics) {
                sampling.set(true);
                samplerThread = Thread.ofVirtual().name("load-resource-sampler").start(() -> {
                    while (sampling.get()) {
                        sampler.sample();
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                });
            }
            stageStarted.set(System.nanoTime());
            stageEnd.set(stageStarted.get() + Duration.ofSeconds(seconds).toNanos());
            start.countDown();
            for (Future<?> future : futures) future.get(seconds + requestTimeoutSeconds + 30L, TimeUnit.SECONDS);
        } finally {
            sampling.set(false);
            if (samplerThread != null) samplerThread.join(2_000);
        }

        double elapsedSeconds = (System.nanoTime() - stageStarted.get()) / 1_000_000_000.0;
        return recorder.result(userCount, elapsedSeconds, sampler);
    }

    private void runVirtualUser(int index, String token, AtomicLong endAt,
                                CountDownLatch ready, CountDownLatch start,
                                StageRecorder recorder) {
        ready.countDown();
        try {
            start.await();
            long base = System.nanoTime();
            long heartbeatAt = base + Duration.ofMillis(
                    (index * 997L) % HEARTBEAT_INTERVAL_MS).toNanos();
            long locationAt = base + Duration.ofMillis((index * 31L) % LOCATION_INTERVAL_MS).toNanos();
            long sensorAt = base + Duration.ofMillis((index * 17L) % SENSOR_INTERVAL_MS).toNanos();

            while (System.nanoTime() < endAt.get()) {
                long now = System.nanoTime();
                if (now >= heartbeatAt) {
                    send(HEARTBEAT, "/api/v1/device/heartbeat", null, token, recorder);
                    heartbeatAt += Duration.ofMillis(HEARTBEAT_INTERVAL_MS).toNanos();
                }
                if (now >= locationAt) {
                    send(LOCATION, "/api/v1/device/locations", LOCATION_BODY, token, recorder);
                    locationAt += Duration.ofMillis(LOCATION_INTERVAL_MS).toNanos();
                }
                if (now >= sensorAt) {
                    send(SENSOR, "/api/v1/device/sensor-events", SENSOR_BODY, token, recorder);
                    sensorAt += Duration.ofMillis(SENSOR_INTERVAL_MS).toNanos();
                }

                long next = Math.min(heartbeatAt, Math.min(locationAt, sensorAt));
                long sleepNanos = Math.min(Math.max(0, next - System.nanoTime()), 50_000_000L);
                if (sleepNanos > 0) Thread.sleep(Duration.ofNanos(sleepNanos));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recorder.failure("interrupted");
        } catch (Exception exception) {
            recorder.failure(exception.getClass().getSimpleName());
        }
    }

    private void send(String endpoint, String path, String body, String token,
                      StageRecorder recorder) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(publisher)
                .build();

        long started = System.nanoTime();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            long latencyNanos = System.nanoTime() - started;
            recorder.response(endpoint, response.statusCode(), latencyNanos,
                    body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length,
                    response.body().length);
        } catch (IOException exception) {
            recorder.failure(exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recorder.failure("interrupted");
        }
    }

    private List<Integer> stages() {
        return List.of(configuredUsers.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .filter(value -> value > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private Path writeReport(List<StageResult> results) throws IOException {
        Path directory = Path.of(reportDirectory);
        Files.createDirectories(directory);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault()).format(Instant.now());
        Path report = directory.resolve("device-traffic-" + timestamp + ".md");

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Device traffic local load-test result\n\n")
                .append("- Executed at: ").append(Instant.now()).append("\n")
                .append("- Environment: embedded Spring Boot server, local H2, HTTP/1.1\n")
                .append("- Duration per stage: ").append(durationSeconds).append(" seconds\n")
                .append("- Request pattern: heartbeat 60s, location 10s, heart rate 5s\n")
                .append("- This is a local baseline, not a production capacity guarantee.\n\n")
                .append("| Users | Requests | RPS | Errors | P50 ms | P95 ms | P99 ms | Max ms | Peak CPU % | Peak heap MiB | Peak DB conn |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (StageResult result : results) markdown.append(result.markdownRow()).append('\n');

        markdown.append("\n## Endpoint details\n\n")
                .append("| Users | Endpoint | Requests | Errors | P95 ms | P99 ms |\n")
                .append("|---:|---|---:|---:|---:|---:|\n");
        for (StageResult result : results) {
            for (EndpointResult endpoint : result.endpoints()) {
                markdown.append(String.format(Locale.ROOT,
                        "| %d | %s | %d | %d | %.2f | %.2f |%n",
                        result.users(), endpoint.name(), endpoint.requests(), endpoint.errors(),
                        endpoint.p95Ms(), endpoint.p99Ms()));
            }
        }
        return Files.writeString(report, markdown.toString(), StandardCharsets.UTF_8);
    }

    private static final class StageRecorder {
        private final Map<String, ConcurrentLinkedQueue<Long>> latencies = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> requests = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> errors = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> statusErrors = new ConcurrentHashMap<>();
        private final AtomicLong requestBytes = new AtomicLong();
        private final AtomicLong responseBytes = new AtomicLong();

        void response(String endpoint, int status, long latencyNanos, int sentBytes, int receivedBytes) {
            requests.computeIfAbsent(endpoint, ignored -> new AtomicLong()).incrementAndGet();
            latencies.computeIfAbsent(endpoint, ignored -> new ConcurrentLinkedQueue<>()).add(latencyNanos);
            requestBytes.addAndGet(sentBytes);
            responseBytes.addAndGet(receivedBytes);
            if (status < 200 || status >= 300) {
                errors.computeIfAbsent(endpoint, ignored -> new AtomicLong()).incrementAndGet();
                statusErrors.computeIfAbsent(endpoint + "-" + status, ignored -> new AtomicLong()).incrementAndGet();
            }
        }

        void failure(String reason) {
            errors.computeIfAbsent("transport", ignored -> new AtomicLong()).incrementAndGet();
            statusErrors.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
        }

        StageResult result(int users, double elapsedSeconds, ResourceSampler sampler) {
            List<EndpointResult> endpointResults = new ArrayList<>();
            List<Long> allLatencies = new ArrayList<>();
            long totalRequests = 0;
            long totalErrors = errors.values().stream().mapToLong(AtomicLong::get).sum();
            long transportErrors = errors.getOrDefault("transport", new AtomicLong()).get();

            for (String endpoint : List.of(HEARTBEAT, LOCATION, SENSOR)) {
                List<Long> values = new ArrayList<>(latencies.getOrDefault(
                        endpoint, new ConcurrentLinkedQueue<>()));
                values.sort(Comparator.naturalOrder());
                allLatencies.addAll(values);
                long endpointRequests = requests.getOrDefault(endpoint, new AtomicLong()).get();
                long endpointErrors = errors.getOrDefault(endpoint, new AtomicLong()).get();
                totalRequests += endpointRequests;
                endpointResults.add(new EndpointResult(endpoint, endpointRequests, endpointErrors,
                        percentileMs(values, 0.95), percentileMs(values, 0.99)));
            }
            allLatencies.sort(Comparator.naturalOrder());
            return new StageResult(users, elapsedSeconds, totalRequests, totalErrors,
                    percentileMs(allLatencies, 0.50), percentileMs(allLatencies, 0.95),
                    percentileMs(allLatencies, 0.99), percentileMs(allLatencies, 1.0),
                    transportErrors, sampler.peakCpu.get(), sampler.peakHeapBytes.get(),
                    sampler.peakDbConnections.get(),
                    requestBytes.get(), responseBytes.get(), endpointResults,
                    statusErrors.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, entry -> entry.getValue().get())));
        }

        private static double percentileMs(List<Long> sorted, double percentile) {
            if (sorted.isEmpty()) return 0;
            int index = Math.min(sorted.size() - 1,
                    Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
            return sorted.get(index) / 1_000_000.0;
        }
    }

    private static final class ResourceSampler {
        private final MeterRegistry meters;
        private final DoubleAccumulator peakCpu = new DoubleAccumulator(Math::max, 0);
        private final DoubleAccumulator peakHeapBytes = new DoubleAccumulator(Math::max, 0);
        private final DoubleAccumulator peakDbConnections = new DoubleAccumulator(Math::max, 0);

        private ResourceSampler(MeterRegistry meters) {
            this.meters = meters;
        }

        void sample() {
            Gauge cpu = meters.find("process.cpu.usage").gauge();
            if (cpu != null && Double.isFinite(cpu.value())) peakCpu.accumulate(cpu.value());
            peakHeapBytes.accumulate(sumGauges(
                    meters.find("jvm.memory.used").tag("area", "heap").gauges(), Gauge::value));
            peakDbConnections.accumulate(sumGauges(
                    meters.find("hikaricp.connections.active").gauges(), Gauge::value));
        }

        private static double sumGauges(Iterable<Gauge> gauges, ToDoubleFunction<Gauge> value) {
            double total = 0;
            for (Gauge gauge : gauges) {
                double current = value.applyAsDouble(gauge);
                if (Double.isFinite(current)) total += current;
            }
            return total;
        }
    }

    private record EndpointResult(String name, long requests, long errors,
                                  double p95Ms, double p99Ms) {
    }

    private record StageResult(int users, double elapsedSeconds, long requests, long errors,
                               double p50Ms, double p95Ms, double p99Ms, double maxMs,
                               long transportErrors, double peakCpu, double peakHeapBytes,
                               double peakDbConnections,
                               long requestBytes, long responseBytes,
                               List<EndpointResult> endpoints, Map<String, Long> errorDetails) {
        double requestsPerSecond() {
            return requests / elapsedSeconds;
        }

        double errorRate() {
            return requests + transportErrors == 0
                    ? 0 : errors / (double) (requests + transportErrors);
        }

        String consoleLine() {
            return String.format(Locale.ROOT,
                    "users=%d requests=%d rps=%.2f errors=%d p95=%.2fms p99=%.2fms cpu=%.1f%% heap=%.1fMiB db=%.0f",
                    users, requests, requestsPerSecond(), errors, p95Ms, p99Ms,
                    peakCpu * 100, peakHeapBytes / 1024 / 1024, peakDbConnections);
        }

        String markdownRow() {
            return String.format(Locale.ROOT,
                    "| %d | %d | %.2f | %d (%.2f%%) | %.2f | %.2f | %.2f | %.2f | %.1f | %.1f | %.0f |",
                    users, requests, requestsPerSecond(), errors, errorRate() * 100,
                    p50Ms, p95Ms, p99Ms, maxMs, peakCpu * 100,
                    peakHeapBytes / 1024 / 1024, peakDbConnections);
        }
    }
}
