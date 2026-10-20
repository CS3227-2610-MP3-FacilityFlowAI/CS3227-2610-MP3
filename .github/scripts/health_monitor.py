"""NFR-OPS-001: external health checks and persistent, deduplicated issue alerts."""
import argparse
import datetime
import json
import os
import re
import urllib.error
import urllib.request

HEALTH_URL = 'https://facilityflow-ai.vercel.app/health'
STATE_TITLE = 'Production health monitor state (NFR-OPS-001)'
STATE_PREFIX = '<!-- facilityflow-health-state-v1 -->\n'
ALERT_PREFIX = '<!-- facilityflow-health-alert-v1 -->\n'


def probe():
    try:
        with urllib.request.urlopen(HEALTH_URL, timeout=20) as response:
            return response.status == 200 and json.loads(response.read(1024)) == {'status': 'UP'}
    except (OSError, ValueError):
        return False


class GitHub:
    def __init__(self):
        self.repository = os.environ['GITHUB_REPOSITORY']
        if not re.fullmatch(r'[\w.-]+/[\w.-]+', self.repository):
            raise ValueError('Invalid repository')
        self.token = os.environ['GH_TOKEN']
        self.state_numbers = {}

    def request(self, method, path, data=None):
        request = urllib.request.Request(
            f'https://api.github.com/repos/{self.repository}/{path}',
            data=json.dumps(data).encode() if data is not None else None,
            method=method, headers={'Authorization': f'Bearer {self.token}',
                                    'Accept': 'application/vnd.github+json',
                                    'Content-Type': 'application/json',
                                    'User-Agent': 'FacilityFlow-operations'})
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)

    def find_issue(self, title, prefix):
        for page in range(1, 101):
            issues = self.request('GET', f'issues?state=open&per_page=100&page={page}')
            for issue in issues:
                if (issue.get('title') == title and not issue.get('pull_request')
                        and issue.get('user', {}).get('login') == 'github-actions[bot]'
                        and isinstance(issue.get('body'), str)
                        and issue['body'].startswith(prefix)):
                    return issue
            if len(issues) < 100:
                return None
        raise ValueError('Monitor state search exceeded limit')

    def state_issue(self, title):
        if title in self.state_numbers:
            return self.request('GET', f'issues/{self.state_numbers[title]}')
        issue = self.find_issue(title, STATE_PREFIX) or self.request('POST', 'issues', {
            'title': title, 'body': STATE_PREFIX + json.dumps({'failures': 0, 'alert': None})})
        # Issue lists can lag creation; reuse the identity during this invocation.
        self.state_numbers[title] = issue['number']
        return issue


def check(github, healthy, exercise=False):
    title = ('[EXERCISE] ' if exercise else '') + STATE_TITLE
    issue = github.state_issue(title)
    state = json.loads(issue['body'][len(STATE_PREFIX):])
    failures, alert = state['failures'], state['alert']
    if (type(failures) is not int or not 0 <= failures <= 3
            or (alert is not None and (type(alert) is not int or alert <= 0))):
        raise ValueError('Invalid monitor state')
    prefix = '[EXERCISE] ' if exercise else ''
    alert_title = prefix + 'Production health failed three consecutive checks'
    # Reconcile a successful alert POST whose subsequent state PATCH failed.
    existing = github.find_issue(alert_title, ALERT_PREFIX)
    if existing:
        alert = existing['number']
    elif alert is not None:
        previous = github.request('GET', f'issues/{alert}')
        if (previous.get('title') != alert_title or previous.get('pull_request')
                or previous.get('user', {}).get('login') != 'github-actions[bot]'
                or not isinstance(previous.get('body'), str)
                or not previous['body'].startswith(ALERT_PREFIX)
                or previous.get('state') not in ('open', 'closed')):
            raise ValueError('Invalid persisted alert reference')
        # Recovery closed the alert before its state write succeeded.
        if previous['state'] == 'closed':
            alert, failures = None, 0
    failures = 0 if healthy else min(failures + 1, 3)
    if failures == 3 and alert is None:
        alert = github.request('POST', 'issues', {
            'title': alert_title,
            'body': (ALERT_PREFIX + 'NFR-OPS-001: three consecutive external health checks failed.\n\n'
                     + ('Synthetic alert exercise; no live outage simulated.\n' if exercise else '')
                     + f'Health endpoint: {HEALTH_URL}\nCheck the operational runbook. No response payloads retained.')})['number']
    elif healthy and alert is not None:
        github.request('POST', f'issues/{alert}/comments', {
            'body': prefix + 'Health check recovered. Monitor reset after a successful check.'})
        github.request('PATCH', f'issues/{alert}', {'state': 'closed'})
        alert = None
    github.request('PATCH', f'issues/{issue["number"]}', {'body': STATE_PREFIX + json.dumps({
        'failures': failures, 'alert': alert,
        'checked_at': datetime.datetime.now(datetime.timezone.utc).isoformat()})})
    print(json.dumps({'healthy': healthy, 'consecutive_failures': failures,
                      'alert_issue': alert, 'exercise': exercise}))
    return healthy


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--exercise', action='store_true')
    parser.add_argument('--backup-failure', action='store_true')
    args = parser.parse_args()
    github = GitHub()
    if args.backup_failure:
        github.request('POST', 'issues', {'title': 'Production backup/restore verification failed',
            'body': 'NFR-REL-002: inspect the failed Production backup workflow. '
                    'A new verified backup is required; do not assume RPO/RTO was met.'})
        return
    if args.exercise:
        check(github, True, True)
        for _ in range(3):
            check(github, False, True)
        check(github, True, True)
    elif not check(github, probe()):
        raise SystemExit(1)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError):
        # Never include API response bodies, tokens or raw network errors.
        raise SystemExit('Operations check failed; inspect configuration and issue permissions.')
