import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';
const require = createRequire(new URL('../../../apps/web/package.json', import.meta.url));
const ts = require('typescript');
const Sentry = require('@sentry/nextjs');
const compile = source => `data:text/javascript;base64,${Buffer.from(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 } }).outputText).toString('base64')}`;
const policy = compile(readFileSync(new URL('../../../packages/core/src/operations/sentry-policy.ts', import.meta.url), 'utf8'));
const optionsSource = readFileSync(new URL('../../../apps/web/sentry.options.ts', import.meta.url), 'utf8').replace('"@gole/core/operations"', JSON.stringify(policy));
const { sentryOptions } = await import(compile(optionsSource));
const dsn = 'https://public@o0.ingest.sentry.io/1';

test('실제 SDK envelope는 원문·첨부를 제거하고 중복을 억제한다', async () => {
  const envelopes = [];
  Sentry.init({ ...sentryOptions('true', 'production', dsn), transport: () => ({ send: async envelope => { envelopes.push(envelope); return { statusCode: 200 }; }, flush: async () => true }) });
  Sentry.setUser({ email: 'SECRET_EMAIL' });
  Sentry.setContext('private', { token: 'SECRET_TOKEN' });
  Sentry.setTag('url', 'SECRET_URL');
  const raw = { level: 'error', message: 'SECRET_MESSAGE', request: { url: 'SECRET_URL', cookies: { token: 'SECRET_COOKIE' } }, exception: { values: [{ value: 'SECRET_EXCEPTION' }] }, extra: { token: 'SECRET_EXTRA' } };
  Sentry.captureEvent(raw, { attachments: [{ filename: 'SECRET_FILE', data: 'SECRET_ATTACHMENT' }] });
  await Sentry.flush(2000);
  assert.equal(envelopes.length, 1);
  const serialized = JSON.stringify(envelopes);
  assert.ok(!serialized.includes('SECRET'), '전송 envelope 전체에서 개인정보를 제거해야 함');
  assert.ok(serialized.includes('UNEXPECTED_ERROR'));
  assert.equal(envelopes[0][1].length, 1);
  Sentry.captureEvent(raw); await Sentry.flush(2000);
  assert.equal(envelopes.length, 1);
  await Sentry.close();
});

test('비활성·local·test·DSN 미설정은 transport를 호출하지 않는다', async () => {
  for (const args of [[undefined, 'production', dsn], ['true', 'local', dsn], ['true', 'test', dsn], ['true', 'production', undefined], ['true', 'production', 'SECRET_INVALID_DSN']]) {
    let sends = 0;
    const client = new Sentry.NodeClient({ ...sentryOptions(...args), integrations: [], transport: () => ({ send: async () => { sends++; return { statusCode: 200 }; }, flush: async () => true }) });
    client.captureEvent({message: 'UNEXPECTED_ERROR', level: 'error'}); await client.flush(2000);
    assert.equal(sends, 0); await client.close();
  }
});

test('transport 실패는 애플리케이션 예외로 전파하지 않는다', async () => {
  let attempts = 0;
  const client = new Sentry.NodeClient({ ...sentryOptions('true', 'production', dsn), integrations: [], transport: () => ({ send: async () => { attempts++; throw new Error('TRANSPORT_FAILURE'); }, flush: async () => true }) });
  client.captureEvent({message: 'UNEXPECTED_ERROR', level: 'error'}); await client.flush(2000);
  assert.equal(attempts, 1); await client.close();
});


test('브라우저 SDK도 전송 envelope에서 개인정보와 첨부를 제거한다', async () => {
  const browser = createRequire(require.resolve('@sentry/nextjs'))('@sentry/browser');
  const envelopes = [];
  const client = new browser.BrowserClient({ ...sentryOptions('true', 'production', dsn), integrations: [], stackParser: browser.defaultStackParser, transport: () => ({ send: async envelope => { envelopes.push(envelope); return { statusCode: 200 }; }, flush: async () => true }) });
  client.captureEvent({ level: 'error', message: 'SECRET', user: { email: 'SECRET' }, request: { url: 'SECRET' }, extra: { cookie: 'SECRET' } }, { attachments: [{ filename: 'SECRET', data: 'SECRET' }] });
  await client.flush(2000);
  assert.equal(envelopes.length, 1);
  assert.ok(!JSON.stringify(envelopes).includes('SECRET'));
  assert.equal(envelopes[0][1].length, 1);
  await client.close();
});
