import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const root = new URL('../../', import.meta.url);
const version = readFileSync(new URL('version.txt', root), 'utf8').trim();
assert.match(version, /^(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})$/);
const code = (v: string) => v.split('.').reduce((n, part) => n * 1000 + Number(part), 0);
assert.ok(code(version) > 0, 'Android version code must be positive');
const manifest = JSON.parse(readFileSync(new URL('.release-please-manifest.json', root), 'utf8'));
assert.equal(manifest['.'], version, 'Manifest and version.txt must agree');
const config = JSON.parse(readFileSync(new URL('release-please-config.json', root), 'utf8'));
assert.equal(config.packages['.'].versioning, 'app');
assert.equal(config.packages['.'].draft, true);
assert.equal(config.packages['.']['force-tag-creation'], true);
const changelog = readFileSync(new URL('CHANGELOG.md', root), 'utf8');
assert.ok(changelog.includes(`## [${version}]`) || changelog.includes(`## ${version}`), 'Changelog must include the current version');

// PR checks run on GitHub's merge ref, so HEAD^1 is the tested base.
if (process.env.GITHUB_EVENT_NAME === 'pull_request') {
  const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH!, 'utf8'));
  const git = (...args: string[]) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
  const files = git('diff', '--name-only', 'HEAD^1', 'HEAD').split('\n').filter(Boolean);
  const releaseFiles = ['version.txt', '.release-please-manifest.json', 'CHANGELOG.md'];
  const isRelease = event.pull_request.head.ref === 'release-please--branches--main';
  if (isRelease) {
    assert.ok(files.length > 0 && files.every(file => releaseFiles.includes(file)), 'Release PR may only change version, manifest and changelog');
    assert.ok(releaseFiles.every(file => files.includes(file)), 'Release PR must update all three release files');
    assert.ok(code(version) > code(git('show', 'HEAD^1:version.txt')), 'Release version code must increase');
  } else {
    assert.ok(files.every(file => !releaseFiles.includes(file)), 'Let Release Please own version, manifest and changelog updates');
  }
}
console.log(`Release configuration valid: ${version} (${code(version)})`);
