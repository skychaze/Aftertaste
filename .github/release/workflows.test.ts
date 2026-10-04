import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { parse } from 'yaml';

const workflow = parse(readFileSync(new URL('../workflows/release.yml', import.meta.url), 'utf8'));
const steps = new Map<string, string>(workflow.jobs['build-release'].steps.map(
  (step: { name: string; run?: string }) => [step.name, step.run ?? ''],
));

function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'aftertaste-workflow-'));
  const bin = join(dir, 'bin');
  mkdirSync(bin);
  const command = (name: string, source: string) => writeFileSync(join(bin, name), `#!/bin/bash\n${source}\n`, { mode: 0o755 });
  command('git', 'exit "${MOCK_GIT_STATUS:-0}"');
  command('gh', 'printf "%s\\n" "$MOCK_RELEASES"');
  const env = {
    ...process.env, PATH: `${bin}:${process.env.PATH}`, GITHUB_ENV: join(dir, 'env'),
    GITHUB_RUN_NUMBER: '1', GITHUB_REPOSITORY: 'test/repo', RUNNER_TEMP: dir,
  };
  return {
    dir, command,
    run(name: string, extra: Record<string, string>) {
      const script = steps.get(name);
      assert.ok(script, `Missing workflow step: ${name}`);
      return spawnSync('bash', ['-e', '-c', script], { cwd: dir, env: { ...env, ...extra }, encoding: 'utf8' });
    },
    cleanup() { rmSync(dir, { recursive: true, force: true }); },
  };
}

test('actual version script validates tags, ranges, manifest consistency and main ancestry', () => {
  const f = fixture();
  try {
    const cases = [
      ['1.2.12', 'v1.2.12', '0', true], ['1.2.12', 'V1.2.12', '0', true],
      ['1.2.12', 'v1.2.11', '0', false], ['1.2.1000', 'v1.2.1000', '0', false],
      ['0.0.0', 'v0.0.0', '0', false], ['1.2.12', 'v1.2.12', '1', false],
      ['1.2.12', '', '0', true],
    ] as const;
    for (const [version, tag, gitStatus, pass] of cases) {
      writeFileSync(join(f.dir, 'version.txt'), `${version}\n`);
      writeFileSync(join(f.dir, '.release-please-manifest.json'), JSON.stringify({ '.': version }));
      const result = f.run('Verify release version', { REQUESTED_TAG: tag, MOCK_GIT_STATUS: gitStatus });
      assert.equal(result.status === 0, pass, `${version}/${tag}: ${result.stdout}${result.stderr}`);
    }
    writeFileSync(join(f.dir, '.release-please-manifest.json'), '{".":"1.2.11"}');
    assert.notEqual(f.run('Verify release version', { REQUESTED_TAG: 'v1.2.12' }).status, 0);
  } finally { f.cleanup(); }
});

test('actual publication guard accepts draft retries and rejects existing or newer public versions', () => {
  const f = fixture();
  try {
    for (const [tag, draft, pass] of [
      ['v1.2.11', false, true], ['v1.2.12', false, false], ['V1.2.12', false, false],
      ['v1.3.0', false, false], ['v1.2.12', true, true],
    ] as const) {
      const result = f.run('Refuse to replace a published release', {
        TAG_NAME: 'v1.2.12', VERSION_CODE: '1002012', MOCK_RELEASES: JSON.stringify({ tag_name: tag, draft }),
      });
      assert.equal(result.status === 0, pass, result.stdout + result.stderr);
    }
  } finally { f.cleanup(); }
});

test('actual signer check accepts matching certificate formats and rejects mismatches and invalid signatures', () => {
  const f = fixture();
  try {
    const sdk = join(f.dir, 'sdk');
    const buildTools = join(sdk, 'build-tools', '36.0.0');
    mkdirSync(buildTools, { recursive: true });
    writeFileSync(join(buildTools, 'apksigner'), '#!/bin/bash\nprintf "%s\\n" "$MOCK_SIGNATURE"\nexit "${MOCK_VERIFY_STATUS:-0}"\n', { mode: 0o755 });
    f.command('keytool', 'while [ "$#" -gt 0 ]; do\n  if [ "$1" = -file ]; then printf "%s" "$MOCK_CERT" > "$2"; exit 0; fi\n  shift\ndone\nexit 1');
    const cert = 'fixture certificate';
    const digest = createHash('sha256').update(cert).digest('hex');
    const numbered = `Signer #1 certificate SHA-256 digest: ${digest}`;
    const ranged = `Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: ${digest}`;
    for (const [output, status, pass] of [
      [numbered, '0', true], [ranged, '0', true],
      [`V2 Signer: certificate SHA-256 digest: ${digest}`, '0', true],
      [`${numbered}\nSource Stamp Signer certificate SHA-256 digest: other`, '0', true], [`${numbered}\n${ranged}`, '0', true],
      ['Signer #1 certificate SHA-256 digest: wrong', '0', false], ['', '0', false],
      [numbered, '1', false], [`${numbered}\nSigner #2 certificate SHA-256 digest: wrong`, '0', false],
    ] as const) {
      const result = f.run('Verify APK signature and expected signer', {
        ANDROID_HOME: sdk, VERSION_NAME: '1.2.12', VERSION_CODE: '1002012',
        KEYSTORE_PATH: join(f.dir, 'fixture.keystore'), KEY_ALIAS: 'fixture', STORE_PASSWORD: 'fixture',
        MOCK_CERT: cert, MOCK_SIGNATURE: output, MOCK_VERIFY_STATUS: status,
      });
      assert.equal(result.status === 0, pass, result.stdout + result.stderr);
    }
  } finally { f.cleanup(); }
});
