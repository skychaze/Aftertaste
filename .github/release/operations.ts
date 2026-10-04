import type { Manifest } from 'release-please';

export async function executeOperation(
  operation: string,
  manifest: Pick<Manifest, 'buildReleases' | 'createReleases' | 'createPullRequests'>,
) {
  if (operation !== 'prepare' && operation !== 'release') {
    throw new Error('RELEASE_OPERATION must be prepare or release');
  }
  const pending = await manifest.buildReleases();
  if (operation === 'prepare') {
    if (pending.length) {
      throw new Error('A merged release PR still needs releasing. Run Release before preparing another version.');
    }
    const prs = (await manifest.createPullRequests()).filter(pr => pr !== undefined);
    return { release: undefined, prs };
  }
  if (!pending.length) {
    throw new Error('No merged release PR needs releasing. Merge the checked release PR first; retry an existing draft with Release APK and its tag.');
  }
  if (pending.length !== 1 || pending[0].path !== '.') {
    throw new Error('Expected exactly one root-package release');
  }
  const releases = (await manifest.createReleases()).filter(release => release !== undefined);
  if (releases.length !== 1 || releases[0].path !== '.') {
    throw new Error('No unique release was created. Inspect tags/drafts before retrying; use Release APK for an existing tag.');
  }
  return { release: releases[0], prs: [] };
}
