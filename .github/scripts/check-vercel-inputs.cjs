// NFR-DEP-001: validate Vercel's actual upload inventory before creating a deployment.
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { readFileSync } = require('node:fs');

const inventory = JSON.parse(readFileSync(0, 'utf8'));
const uploaded = new Set(inventory.files.map(file => file.path));
const required = execFileSync('git', [
  'ls-files', '--', '.dockerignore', 'Dockerfile.vercel', 'gradlew',
  'gradle', 'build.gradle', 'settings.gradle', 'src',
], { encoding: 'utf8' }).trim().split('\n');
for (const path of required) {
  assert(uploaded.has(path), `Required build input missing from Vercel upload: ${path}`);
}
for (const path of uploaded) {
  assert(/^(?:\.dockerignore$|Dockerfile\.vercel$|gradlew$|build\.gradle$|settings\.gradle$|gradle\/|src\/)/.test(path),
    `Unexpected file in Vercel upload: ${path}`);
}
console.log(`NFR-DEP-001: Vercel upload includes all ${required.length} tracked build inputs and excludes other paths`);
