import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const baseUrl = process.env.BASE_URL || 'http://localhost:8080';
const connections = Number(process.env.CONNECTIONS || 100);
const durationSeconds = Number(process.env.DURATION_SECONDS || 60);
const tokenFile = process.env.TOKENS_FILE;
if (!tokenFile) throw new Error('TOKENS_FILE is required.');

const data = JSON.parse(readFileSync(resolve(tokenFile), 'utf8'));
const tokens = data.tokens.slice(0, connections).map((entry) => typeof entry === 'string' ? entry : entry.token);
if (tokens.length < connections) throw new Error('Not enough device tokens.');

const controllers = [];
let opened = 0;
let failed = 0;
let events = 0;
let closedEarly = 0;

async function connect(token) {
  const controller = new AbortController();
  controllers.push(controller);
  try {
    const response = await fetch(`${baseUrl}/api/v1/device/events`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      signal: controller.signal,
    });
    if (!response.ok || !response.body) { failed += 1; return; }
    opened += 1;
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    while (true) {
      const { done, value } = await reader.read();
      if (done) { closedEarly += 1; break; }
      events += (decoder.decode(value, { stream: true }).match(/event:/g) || []).length;
    }
  } catch (error) {
    if (error?.name !== 'AbortError') failed += 1;
  }
}

const jobs = tokens.map(connect);
const deadline = Date.now() + durationSeconds * 1000;
while (Date.now() < deadline && opened + failed < connections) {
  await new Promise((done) => setTimeout(done, 100));
}
await new Promise((done) => setTimeout(done, Math.max(0, deadline - Date.now())));
for (const controller of controllers) controller.abort();
await Promise.allSettled(jobs);

const result = { executedAt: new Date().toISOString(), connections, durationSeconds, opened, failed, closedEarly, events };
const directory = resolve('target/load-test-results/sse');
mkdirSync(directory, { recursive: true });
const path = resolve(directory, `sse-${connections}-${durationSeconds}s-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);
writeFileSync(path, JSON.stringify(result, null, 2), 'utf8');
console.log(JSON.stringify({ ...result, path }, null, 2));
if (failed > 0 || closedEarly > 0) process.exitCode = 2;
