import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { performance } from 'node:perf_hooks';

const baseUrl = process.env.BASE_URL || 'http://localhost:8080';
const users = Number(process.env.USERS || 1);
const durationSeconds = Number(process.env.DURATION_SECONDS || 60);
const timeoutMs = Number(process.env.REQUEST_TIMEOUT_MS || 10000);
const tokenFile = process.env.TOKENS_FILE;
const outputDirectory = resolve(process.env.RESULT_DIR || 'target/load-test-results/postgres');

if (!tokenFile) throw new Error('TOKENS_FILE is required.');
if (!Number.isInteger(users) || users < 1) throw new Error('USERS must be a positive integer.');
if (!Number.isFinite(durationSeconds) || durationSeconds < 1) {
  throw new Error('DURATION_SECONDS must be positive.');
}

const tokenData = JSON.parse(readFileSync(resolve(tokenFile), 'utf8'));
if (!Array.isArray(tokenData.tokens) || tokenData.tokens.length < users) {
  throw new Error(`Token file has ${tokenData.tokens?.length || 0} tokens, but ${users} are required.`);
}

const endpoints = {
  heartbeat: { path: '/api/v1/device/heartbeat', intervalMs: 60000, body: null, multiplier: 997 },
  location: {
    path: '/api/v1/device/locations',
    intervalMs: 10000,
    body: JSON.stringify({ latitude: 37.6026, longitude: 126.9553, accuracyMeters: 15 }),
    multiplier: 31,
  },
  sensor: {
    path: '/api/v1/device/sensor-events',
    intervalMs: 5000,
    body: JSON.stringify({ type: 'HEART_RATE', numericValue: 80 }),
    multiplier: 17,
  },
};

const records = new Map(Object.keys(endpoints).map((name) => [name, {
  attempts: 0,
  errors: 0,
  latencies: [],
  requestBodyBytes: 0,
  responseBodyBytes: 0,
  statuses: {},
}]));

const resources = {
  peakCpu: 0,
  peakHeapBytes: 0,
  peakDbConnections: 0,
  samples: 0,
  samplingErrors: 0,
};

function sleep(ms) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, Math.max(0, ms)));
}

async function send(name, definition, token) {
  const record = records.get(name);
  record.attempts += 1;
  if (definition.body) record.requestBodyBytes += Buffer.byteLength(definition.body);

  const started = performance.now();
  try {
    const response = await fetch(`${baseUrl}${definition.path}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: definition.body,
      signal: AbortSignal.timeout(timeoutMs),
    });
    const responseBody = await response.arrayBuffer();
    record.latencies.push(performance.now() - started);
    record.responseBodyBytes += responseBody.byteLength;
    record.statuses[response.status] = (record.statuses[response.status] || 0) + 1;
    if (!response.ok) record.errors += 1;
  } catch (error) {
    record.errors += 1;
    const key = error?.name || 'transport-error';
    record.statuses[key] = (record.statuses[key] || 0) + 1;
  }
}

async function runPeriodic(index, token, name, definition, startAt, endAt) {
  let nextAt = startAt + ((index * definition.multiplier) % definition.intervalMs);
  await sleep(nextAt - Date.now());
  while (Date.now() < endAt) {
    await send(name, definition, token);
    nextAt += definition.intervalMs;
    await sleep(Math.min(nextAt, endAt) - Date.now());
  }
}

function metricValues(text, metricName, predicate = () => true) {
  const values = [];
  for (const line of text.split('\n')) {
    if (!line.startsWith(metricName) || line.startsWith('#')) continue;
    const separator = line.lastIndexOf(' ');
    if (separator < 0 || !predicate(line.slice(0, separator))) continue;
    const value = Number(line.slice(separator + 1));
    if (Number.isFinite(value)) values.push(value);
  }
  return values;
}

async function sampleResources() {
  try {
    const response = await fetch(`${baseUrl}/actuator/prometheus`, {
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) throw new Error(`metrics-${response.status}`);
    const text = await response.text();
    const cpu = metricValues(text, 'process_cpu_usage')[0] || 0;
    const heap = metricValues(text, 'jvm_memory_used_bytes', (labels) => labels.includes('area="heap"'))
      .reduce((sum, value) => sum + value, 0);
    const dbConnections = metricValues(text, 'hikaricp_connections_active')
      .reduce((sum, value) => sum + value, 0);
    resources.peakCpu = Math.max(resources.peakCpu, cpu);
    resources.peakHeapBytes = Math.max(resources.peakHeapBytes, heap);
    resources.peakDbConnections = Math.max(resources.peakDbConnections, dbConnections);
    resources.samples += 1;
  } catch {
    resources.samplingErrors += 1;
  }
}

function percentile(values, ratio) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((left, right) => left - right);
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * ratio) - 1));
  return sorted[index];
}

function fixed(value, digits = 2) {
  return Number(value).toFixed(digits);
}

const startAt = Date.now() + 2000;
const endAt = startAt + durationSeconds * 1000;
let metricsTimer;
let progressTimer;

await sampleResources();
metricsTimer = setInterval(sampleResources, 1000);
progressTimer = setInterval(() => {
  const progressAttempts = [...records.values()]
    .reduce((sum, record) => sum + record.attempts, 0);
  const progressErrors = [...records.values()]
    .reduce((sum, record) => sum + record.errors, 0);
  const elapsedSeconds = Math.min(
    durationSeconds,
    Math.max(0, Math.round((Date.now() - startAt) / 1000)),
  );
  const statuses = {};
  for (const record of records.values()) {
    for (const [status, count] of Object.entries(record.statuses)) {
      statuses[status] = (statuses[status] || 0) + count;
    }
  }
  console.log(JSON.stringify({
    progress: true,
    elapsedSeconds,
    durationSeconds,
    attempts: progressAttempts,
    errors: progressErrors,
    statuses,
  }));
}, 60000);

const jobs = [];
for (let index = 0; index < users; index += 1) {
  const tokenEntry = tokenData.tokens[index];
  const token = typeof tokenEntry === 'string' ? tokenEntry : tokenEntry.token;
  for (const [name, definition] of Object.entries(endpoints)) {
    jobs.push(runPeriodic(index, token, name, definition, startAt, endAt));
  }
}

await Promise.all(jobs);
clearInterval(metricsTimer);
clearInterval(progressTimer);
await sampleResources();

const endpointResults = {};
let attempts = 0;
let errors = 0;
let requestBodyBytes = 0;
let responseBodyBytes = 0;
const allLatencies = [];

for (const [name, record] of records.entries()) {
  attempts += record.attempts;
  errors += record.errors;
  requestBodyBytes += record.requestBodyBytes;
  responseBodyBytes += record.responseBodyBytes;
  allLatencies.push(...record.latencies);
  endpointResults[name] = {
    attempts: record.attempts,
    errors: record.errors,
    p50Ms: percentile(record.latencies, 0.50),
    p95Ms: percentile(record.latencies, 0.95),
    p99Ms: percentile(record.latencies, 0.99),
    maxMs: percentile(record.latencies, 1),
    statuses: record.statuses,
  };
}

const result = {
  executedAt: new Date().toISOString(),
  runId: tokenData.runId,
  environment: 'separate Node load-generator process -> Spring Boot process -> PostgreSQL 16 in WSL',
  baseUrl,
  users,
  durationSeconds,
  requestPattern: 'heartbeat 60s, location 10s, heart rate 5s',
  attempts,
  errors,
  errorRate: attempts === 0 ? 0 : errors / attempts,
  rps: attempts / durationSeconds,
  p50Ms: percentile(allLatencies, 0.50),
  p95Ms: percentile(allLatencies, 0.95),
  p99Ms: percentile(allLatencies, 0.99),
  maxMs: percentile(allLatencies, 1),
  requestBodyBytes,
  responseBodyBytes,
  resources,
  endpoints: endpointResults,
};

mkdirSync(outputDirectory, { recursive: true });
const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
const baseName = `postgres-${users}users-${durationSeconds}s-${timestamp}`;
const jsonPath = resolve(outputDirectory, `${baseName}.json`);
const markdownPath = resolve(outputDirectory, `${baseName}.md`);

writeFileSync(jsonPath, JSON.stringify(result, null, 2), 'utf8');

const markdown = `# PostgreSQL device traffic load-test result

- Executed at: ${result.executedAt}
- Environment: ${result.environment}
- Users/devices: ${users}
- Duration: ${durationSeconds} seconds
- Pattern: ${result.requestPattern}

| Attempts | RPS | Errors | P50 | P95 | P99 | Max | Peak CPU | Peak heap | Peak DB connections |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ${attempts} | ${fixed(result.rps)} | ${errors} (${fixed(result.errorRate * 100)}%) | ${fixed(result.p50Ms)}ms | ${fixed(result.p95Ms)}ms | ${fixed(result.p99Ms)}ms | ${fixed(result.maxMs)}ms | ${fixed(resources.peakCpu * 100, 1)}% | ${fixed(resources.peakHeapBytes / 1024 / 1024, 1)}MiB | ${fixed(resources.peakDbConnections, 0)} |

## Endpoint details

| Endpoint | Attempts | Errors | P95 | P99 | Max |
|---|---:|---:|---:|---:|---:|
${Object.entries(endpointResults).map(([name, endpoint]) =>
  `| ${name} | ${endpoint.attempts} | ${endpoint.errors} | ${fixed(endpoint.p95Ms)}ms | ${fixed(endpoint.p99Ms)}ms | ${fixed(endpoint.maxMs)}ms |`).join('\n')}

This result measures the configured device telemetry workload. It does not include SSE, card usage, STT, reports, or external AI calls.
`;

writeFileSync(markdownPath, markdown, 'utf8');
console.log(JSON.stringify({ ...result, jsonPath, markdownPath }, null, 2));

if (result.errorRate >= 0.01) process.exitCode = 2;
