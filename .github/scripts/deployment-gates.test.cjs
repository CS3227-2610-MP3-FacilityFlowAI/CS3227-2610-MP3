// NFR-DEP-001: enabling Preview must not stage unconfigured Production.
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const workflow = readFileSync('.github/workflows/ci-cd.yml', 'utf8');
function condition(job) {
  const body = workflow.split(`\n  ${job}:`)[1].split(/\n  [\w-]+:/)[0];
  const expression = body.match(/\n    if: >-\r?\n((?:      .*\r?\n)+)/)[1].trim();
  return new Function('github', 'vars', `return (${expression});`);
}
const production = condition('production-candidate');
const preview = condition('preview');
const push = { event_name: 'push', ref: 'refs/heads/master' };
for (const enabled of [undefined, 'false', 'true']) {
  for (const productionEnabled of [undefined, 'false', 'true']) {
    assert.equal(production(push, {
      VERCEL_DEPLOY_ENABLED: enabled, PRODUCTION_DEPLOY_ENABLED: productionEnabled,
    }), enabled === 'true' && productionEnabled === 'true');
  }
}
const enabled = { VERCEL_DEPLOY_ENABLED: 'true', PRODUCTION_DEPLOY_ENABLED: 'true' };
assert.equal(production({ ...push, ref: 'refs/heads/feature' }, enabled), false);
assert.equal(production({ ...push, event_name: 'pull_request' }, enabled), false);
assert.equal(production({ ...push, event_name: 'workflow_dispatch' }, enabled), false);
const pr = {
  event_name: 'pull_request', repository: 'team/repo',
  event: { pull_request: { head: { repo: { full_name: 'team/repo' } } } },
};
assert.equal(preview(pr, { VERCEL_DEPLOY_ENABLED: 'true' }), true);
assert.equal(preview(pr, { VERCEL_DEPLOY_ENABLED: 'false' }), false);
pr.event.pull_request.head.repo.full_name = 'fork/repo';
assert.equal(preview(pr, { VERCEL_DEPLOY_ENABLED: 'true' }), false);
const preflight = workflow.split('      - name: Validate Production smoke credentials')[1]
  .split('      - name: Link Production environment')[0]
  .match(/        run: \|\r?\n((?:          .*\r?\n)+)/)[1]
  .replace(/^          /gm, '');
for (const [username, password, expected] of [
  ['', '', 1], ['synthetic', '', 1], ['', 'secret-canary', 1],
  ['synthetic', 'secret-canary', 0],
]) {
  const result = spawnSync('bash', ['-c', preflight], {
    env: { ...process.env, SMOKE_USERNAME: username, SMOKE_PASSWORD: password },
    encoding: 'utf8',
  });
  assert.ifError(result.error);
  assert.equal(result.status, expected);
  assert(!`${result.stdout}${result.stderr}`.includes('secret-canary'));
}
console.log('NFR-DEP-001: Production opt-in, Preview/fork gates and secret-safe preflight passed');
