import { createHash, randomBytes } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const count = Number(process.argv[2] || 1000);
if (!Number.isInteger(count) || count < 1 || count > 10000) {
  throw new Error('Device count must be an integer between 1 and 10000.');
}

const runId = `${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}-${randomBytes(3).toString('hex')}`;
const outputDirectory = resolve('target/load-test-runtime', runId);
mkdirSync(outputDirectory, { recursive: true });

const tokens = [];
const sql = ['BEGIN;'];

for (let index = 0; index < count; index += 1) {
  const rawToken = randomBytes(32).toString('base64url');
  const tokenHash = createHash('sha256').update(rawToken, 'utf8').digest('hex');
  const suffix = String(index + 1).padStart(5, '0');
  const deviceId = `load-${runId}-${suffix}`;
  const guardianSubject = `load-${runId}-${suffix}`;
  const guardianEmail = `${guardianSubject}@load.test`;
  tokens.push({ token: rawToken, guardianSubject });

  sql.push(`
WITH new_account AS (
  INSERT INTO accounts (
    email, name, account_type, onboarding_completed, status, created_at, updated_at
  )
  VALUES ('${guardianEmail}', 'Load guardian ${suffix}', 'GUARDIAN', TRUE, 'ACTIVE', NOW(), NOW())
  RETURNING id
), new_oauth AS (
  INSERT INTO oauth_accounts (
    account_id, provider, provider_subject, provider_email, created_at, updated_at
  )
  SELECT id, 'GOOGLE', '${guardianSubject}', '${guardianEmail}', NOW(), NOW()
  FROM new_account
  RETURNING account_id
), new_guardian AS (
  INSERT INTO guardians (name, email, account_id, created_at, updated_at)
  SELECT 'Load guardian ${suffix}', '${guardianEmail}', id, NOW(), NOW()
  FROM new_account
  RETURNING id
), new_user AS (
  INSERT INTO aac_users (name, mode, grid_size, active, created_at, updated_at)
  VALUES ('Load user ${runId}-${suffix}', 'SIMPLE', 'GRID_2X2', TRUE, NOW(), NOW())
  RETURNING id
), new_link AS (
  INSERT INTO user_guardians (
    user_id, guardian_id, role, primary_guardian, created_at, updated_at
  )
  SELECT new_user.id, new_guardian.id, 'PRIMARY', TRUE, NOW(), NOW()
  FROM new_user, new_guardian
  RETURNING user_id
), new_category AS (
  INSERT INTO board_categories (
    aac_user_id, name, color, display_order, active, created_at, updated_at
  )
  SELECT id, '기본', '#DDEEFF', 0, TRUE, NOW(), NOW()
  FROM new_user
  RETURNING id, aac_user_id
), new_card AS (
  INSERT INTO aac_cards (
    aac_user_id, category_id, text, tts_text, emergency, favorite,
    display_order, active, created_at, updated_at
  )
  SELECT aac_user_id, id, '도와주세요', '도와주세요', FALSE, FALSE,
         0, TRUE, NOW(), NOW()
  FROM new_category
  RETURNING id
), new_device AS (
  INSERT INTO aac_devices (
    aac_user_id, device_id, device_name, device_type, status,
    paired_at, last_seen_at, created_at, updated_at
  )
  SELECT id, '${deviceId}', 'Load device ${suffix}', 'TABLET', 'ACTIVE',
         NOW(), NOW(), NOW(), NOW()
  FROM new_user
  RETURNING id
)
INSERT INTO device_access_tokens (
  device_id, token_hash, expires_at, revoked_at, created_at, updated_at
)
SELECT id, '${tokenHash}', NOW() + INTERVAL '1 day', NULL, NOW(), NOW()
FROM new_device;`);
}

sql.push('COMMIT;');

const sqlPath = resolve(outputDirectory, 'seed.sql');
const tokenPath = resolve(outputDirectory, 'tokens.json');
writeFileSync(sqlPath, `${sql.join('\n')}\n`, 'utf8');
writeFileSync(tokenPath, JSON.stringify({ runId, count, tokens }, null, 2), 'utf8');

process.stdout.write(JSON.stringify({ runId, count, outputDirectory, sqlPath, tokenPath }));
