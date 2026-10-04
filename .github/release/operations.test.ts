import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { Manifest } from 'release-please';
import { executeOperation } from './operations.ts';

type Client = Parameters<typeof executeOperation>[1];
function fake(pending: boolean) {
  const calls: string[] = [];
  const client = {
    buildReleases: async () => { calls.push('inspect'); return pending ? [{ path: '.' }] : []; },
    createReleases: async () => { calls.push('release'); return [{ path: '.', tagName: 'v1.2.4' }]; },
    createPullRequests: async () => { calls.push('prepare'); return [{ number: 42 }]; },
  } as unknown as Pick<Manifest, keyof Client>;
  return { client, calls };
}

test('prepare only prepares a PR and never creates tags/releases', async () => {
  const { client, calls } = fake(false);
  const result = await executeOperation('prepare', client);
  assert.deepEqual(calls, ['inspect', 'prepare']);
  assert.equal(result.release, undefined);
  assert.equal(result.prs[0].number, 42);
});
test('release only releases the merged PR and never prepares the next version', async () => {
  const { client, calls } = fake(true);
  assert.equal((await executeOperation('release', client)).release?.tagName, 'v1.2.4');
  assert.deepEqual(calls, ['inspect', 'release']);
});
test('cannot prepare over an unreleased merged PR', async () => {
  const { client, calls } = fake(true);
  await assert.rejects(executeOperation('prepare', client), /Run Release/);
  assert.deepEqual(calls, ['inspect']);
});
test('release retry without a pending PR explains how to recover the existing draft', async () => {
  const { client, calls } = fake(false);
  await assert.rejects(executeOperation('release', client), /Release APK/);
  assert.deepEqual(calls, ['inspect']);
});
test('invalid operation performs no API calls', async () => {
  const { client, calls } = fake(false);
  await assert.rejects(executeOperation('automatic', client), /must be/);
  assert.deepEqual(calls, []);
});
