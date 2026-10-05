import { appendFileSync } from 'node:fs';
import { GitHub, Manifest } from 'release-please';
import { executeOperation } from './operations.ts';
import './versioning.ts';

const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '').split('/');
const token = process.env.GH_TOKEN;
const output = process.env.GITHUB_OUTPUT;
const summary = process.env.GITHUB_STEP_SUMMARY;
if (!owner || !repo || !token || !output) {
  throw new Error('GITHUB_REPOSITORY, GH_TOKEN and GITHUB_OUTPUT are required');
}

const github = await GitHub.create({ owner, repo, token });
const manifest = await Manifest.fromManifest(github, 'main');
const { release, prs } = await executeOperation(process.env.RELEASE_OPERATION ?? '', manifest);
appendFileSync(output, [
  `release_created=${Boolean(release)}`,
  `tag_name=${release?.tagName ?? ''}`,
  '',
].join('\n'));
if (summary) {
  appendFileSync(summary, release
    ? `Created draft **${release.tagName}**. The APK job will test, build, verify and publish it. If that job fails, run Release APK with this same tag.\n`
    : prs.length
      ? prs.map(pr => `Prepared [release PR #${pr.number}](https://github.com/${owner}/${repo}/pull/${pr.number}). Approve its PR workflows if requested, wait for required CI, then squash-merge and run Release.\n`).join('')
      : 'No release PR changes were needed. Inspect the existing release PR, or merge a conventional fix/feature first.\n');
}
