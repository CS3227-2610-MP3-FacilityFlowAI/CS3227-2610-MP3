"""NFR-OPS-001: actual state transition/API contract, no external requests."""
import copy
import json
import unittest
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from unittest.mock import patch

import health_monitor as monitor


class FakeGitHub:
    def __init__(self):
        self.issue = {'number': 1, 'body': monitor.STATE_PREFIX + json.dumps({'failures': 0, 'alert': None})}
        self.events = []
        self.alerts = []

    def find_issue(self, title, prefix):
        return next((copy.deepcopy(a) for a in self.alerts if a['title'] == title
                     and a['body'].startswith(prefix) and a.get('state') != 'closed'), None)

    def state_issue(self, title):
        self.title = title
        return copy.deepcopy(self.issue)

    def request(self, method, path, data=None):
        self.events.append((method, path, data))
        if path == 'issues':
            number = len(self.alerts) + 2
            self.alerts.append({'number': number, 'state': 'open', 'user': {'login': 'github-actions[bot]'}, **data})
            return {'number': number}
        if path == 'issues/1':
            self.issue.update(data)
        for alert in self.alerts:
            if path == f'issues/{alert["number"]}':
                if method == 'GET':
                    return copy.deepcopy(alert)
                if method == 'PATCH':
                    alert.update(data)
        return {}


class HealthMonitorTest(unittest.TestCase):
    def test_third_failure_alerts_once_and_recovery_closes(self):
        github = FakeGitHub()
        for _ in range(2):
            self.assertFalse(monitor.check(github, False))
        self.assertFalse(any(path == 'issues' for _, path, _ in github.events))
        monitor.check(github, False)
        monitor.check(github, False)
        self.assertEqual(1, sum(path == 'issues' for _, path, _ in github.events))
        self.assertTrue(monitor.check(github, True))
        self.assertIn(('PATCH', 'issues/2', {'state': 'closed'}), github.events)
        self.assertEqual(0, json.loads(github.issue['body'][len(monitor.STATE_PREFIX):])['failures'])

    def test_success_breaks_failure_streak(self):
        github = FakeGitHub()
        for healthy in [False, False, True, False, False]:
            monitor.check(github, healthy)
        self.assertFalse(any(path == 'issues' for _, path, _ in github.events))

    def test_alert_creation_survives_failed_state_write(self):
        github = FakeGitHub()
        monitor.check(github, False)
        monitor.check(github, False)
        original = github.request

        def fail_state_write(method, path, data=None):
            if path == 'issues/1':
                raise OSError('synthetic state write failure')
            return original(method, path, data)

        with patch.object(github, 'request', side_effect=fail_state_write):
            with self.assertRaises(OSError):
                monitor.check(github, False)
        monitor.check(github, False)
        self.assertEqual(1, len(github.alerts))
        monitor.check(github, True)
        self.assertEqual('closed', github.alerts[0]['state'])

    def test_recovery_survives_failed_state_write_and_alerts_on_new_outage(self):
        github = FakeGitHub()
        for _ in range(3):
            monitor.check(github, False)
        original = github.request

        def fail_state_write(method, path, data=None):
            if path == 'issues/1':
                raise OSError('synthetic state write failure')
            return original(method, path, data)

        with patch.object(github, 'request', side_effect=fail_state_write):
            with self.assertRaises(OSError):
                monitor.check(github, True)
        for _ in range(2):
            monitor.check(github, False)
        self.assertEqual(1, len(github.alerts))
        monitor.check(github, False)
        self.assertEqual(2, len(github.alerts))
        self.assertEqual('closed', github.alerts[0]['state'])

    def test_untrusted_alert_reference_is_not_modified(self):
        github = FakeGitHub()
        github.issue['body'] = monitor.STATE_PREFIX + '{"failures": 3, "alert": 9}'
        github.alerts.append({'number': 9, 'title': 'Unrelated', 'body': '',
                             'user': {'login': 'someone'}, 'state': 'open'})
        with self.assertRaises(ValueError):
            monitor.check(github, True)
        self.assertFalse(any(method in ('POST', 'PATCH') for method, _, _ in github.events))

    def test_issue_list_lag_does_not_recreate_state_within_invocation(self):
        github = monitor.GitHub.__new__(monitor.GitHub)
        github.state_numbers = {}
        issue = {'number': 42, 'body': monitor.STATE_PREFIX + '{"failures": 0, "alert": null}'}
        with patch.object(github, 'find_issue', return_value=None) as find:
            with patch.object(github, 'request', return_value=issue) as request:
                github.state_issue('synthetic')
                github.state_issue('synthetic')
                self.assertEqual(1, find.call_count)
                self.assertEqual('POST', request.call_args_list[0].args[0])
                self.assertEqual(('GET', 'issues/42'), request.call_args_list[1].args)

    def test_issue_list_lag_still_recovers_known_open_alert(self):
        github = FakeGitHub()
        for _ in range(3):
            monitor.check(github, False)
        with patch.object(github, 'find_issue', return_value=None):
            monitor.check(github, True)
        self.assertEqual('closed', github.alerts[0]['state'])

    def test_invalid_persistent_state_is_not_overwritten(self):
        github = FakeGitHub()
        github.issue['body'] = monitor.STATE_PREFIX + '{"failures": -1, "alert": null}'
        with self.assertRaises(ValueError):
            monitor.check(github, False)
        self.assertEqual([], github.events)

    def test_exercise_uses_distinct_state(self):
        github = FakeGitHub()
        monitor.check(github, False, True)
        self.assertTrue(github.title.startswith('[EXERCISE]'))

    def test_transport_failure_is_unhealthy(self):
        with patch.object(monitor.urllib.request, 'urlopen', side_effect=OSError('synthetic-canary')):
            self.assertFalse(monitor.probe())

    def test_backup_rejects_unsafe_targets_and_secrets_are_not_echoed(self):
        bash = (r'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash'))
        self.assertTrue(bash)
        baseline = {'PGDATABASE': 'facilityflow_prod', 'PGUSER': 'facilityflow_prod_backup.synthetic',
                    'PGSSLMODE': 'verify-full', 'PG_CONTAINER': 'a' * 64,
                    'PGPASSWORD': 'private-password-canary',
                    'BACKUP_ENCRYPTION_PASSPHRASE': 'private-encryption-canary-' * 2,
                    'RESTORE_PASSWORD': 'disposable-restore-only'}
        for changed in [{'PGDATABASE': 'postgres'}, {'PGUSER': 'postgres'},
                        {'PGSSLMODE': 'disable'}, {'PG_CONTAINER': 'not-a-service'},
                        {'BACKUP_ENCRYPTION_PASSPHRASE': 'short'}, {'RESTORE_PASSWORD': 'other'}]:
            with self.subTest(changed=list(changed)):
                result = subprocess.run([bash, str(Path(__file__).with_name('production-backup.sh'))],
                    env={**os.environ, **baseline, **changed}, capture_output=True, text=True)
                self.assertEqual(2, result.returncode)
                self.assertNotIn('private-password-canary', result.stdout + result.stderr)
                self.assertNotIn('private-encryption-canary', result.stdout + result.stderr)

    def test_backup_failure_reports_only_allowlisted_reason(self):
        bash = (r'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash'))
        script = Path(__file__).with_name('production-backup.sh').resolve().as_posix()
        with tempfile.TemporaryDirectory() as temp:
            result = subprocess.run([bash, '-c',
                'docker() { if [[ "$1" == cp ]]; then return 0; fi; '
                'echo "permission denied private-error-canary" >&2; return 1; }; '
                'export -f docker; bash "$1"', 'test', script],
                env={**os.environ, 'PGDATABASE': 'facilityflow_prod',
                     'PGUSER': 'facilityflow_prod_backup.synthetic', 'PGSSLMODE': 'verify-full',
                     'PG_CONTAINER': 'a' * 64, 'PGPASSWORD': 'private-password-canary',
                     'BACKUP_ENCRYPTION_PASSPHRASE': 'private-encryption-canary-' * 2,
                     'RESTORE_PASSWORD': 'disposable-restore-only', 'RUNNER_TEMP': temp,
                     'GITHUB_RUN_ID': 'synthetic', 'GITHUB_RUN_ATTEMPT': '1'},
                capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn('reason=PERMISSION_DENIED', result.stderr)
        self.assertNotIn('private-error-canary', result.stdout + result.stderr)
        self.assertNotIn('private-password-canary', result.stdout + result.stderr)

    def test_encrypted_backup_checksum_verifies_after_relocation(self):
        bash = (r'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash'))
        script = Path(__file__).with_name('production-backup.sh').resolve().as_posix()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            # Isolate the archive boundary; real checksum tools verify the moved files.
            result = subprocess.run([bash, '-c',
                'docker() { if [[ "$*" == *pg_dump* ]]; then printf synthetic-dump; fi; }; '
                'gpg() { local output last; while (($#)); do case "$1" in '
                '--output) output="$2"; shift 2;; --decrypt) cat "$2"; return;; '
                '*) last="$1"; shift;; esac; done; cat "$last" > "$output"; }; '
                'export -f docker gpg; bash "$1"', 'test', script],
                env={**os.environ, 'PGDATABASE': 'facilityflow_prod',
                     'PGUSER': 'facilityflow_prod_backup.synthetic', 'PGSSLMODE': 'verify-full',
                     'PG_CONTAINER': 'a' * 64, 'PGPASSWORD': 'synthetic-only',
                     'BACKUP_ENCRYPTION_PASSPHRASE': 'synthetic-encryption-passphrase-' * 2,
                     'RESTORE_PASSWORD': 'disposable-restore-only', 'RUNNER_TEMP': root.as_posix(),
                     'GITHUB_STEP_SUMMARY': (root / 'summary').as_posix(),
                     'GITHUB_RUN_ID': 'synthetic', 'GITHUB_RUN_ATTEMPT': '1'},
                capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            destination = root / 'downloaded'
            destination.mkdir()
            for artifact in root.glob('*.gpg*'):
                shutil.copy2(artifact, destination)
            verified = subprocess.run([bash, '-c',
                'sha256sum -c facilityflow-production-synthetic-1.dump.gpg.sha256'],
                cwd=destination, capture_output=True, text=True)
            self.assertEqual(0, verified.returncode, verified.stdout + verified.stderr)


if __name__ == '__main__':
    unittest.main()
