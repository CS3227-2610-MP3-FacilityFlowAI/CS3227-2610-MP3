#!/usr/bin/env bash
set -euo pipefail

base_url="${1%/}"
username="${SMOKE_USERNAME:-}"
password="${SMOKE_PASSWORD:-}"
bypass_secret="${SMOKE_BYPASS_SECRET:-}"

if [[ -z "$base_url" || -z "$username" || -z "$password" ]]; then
    echo "Smoke test requires a deployment URL and configured smoke-test credentials." >&2
    exit 2
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cookie_jar="$work_dir/cookies.txt"
login_page="$work_dir/login.html"
dashboard_page="$work_dir/dashboard.html"
health_body="$work_dir/health.json"

curl_args=(--silent --show-error --fail-with-body --retry 8 --retry-delay 3 \
    --retry-all-errors --connect-timeout 10 --max-time 45)
if [[ -n "$bypass_secret" ]]; then
    curl_args+=(--header "x-vercel-protection-bypass: $bypass_secret")
fi

curl "${curl_args[@]}" "$base_url/health" --output "$health_body"
if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$health_body"; then
    echo "Health probe did not report UP." >&2
    exit 1
fi

curl "${curl_args[@]}" "$base_url/login" --cookie-jar "$cookie_jar" --output "$login_page"
csrf_token="$(sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' "$login_page" | head -n 1)"
if [[ -z "$csrf_token" ]]; then
    echo "Login page did not contain a CSRF token." >&2
    exit 1
fi

login_status="$(curl "${curl_args[@]}" "$base_url/login" \
    --cookie "$cookie_jar" --cookie-jar "$cookie_jar" \
    --request POST --data-urlencode "_csrf=$csrf_token" \
    --data-urlencode "username=$username" --data-urlencode "password=$password" \
    --output /dev/null --write-out '%{http_code}')"
if [[ "$login_status" != "302" && "$login_status" != "303" ]]; then
    echo "Smoke-test login failed with HTTP $login_status." >&2
    exit 1
fi

dashboard_status="$(curl "${curl_args[@]}" "$base_url/" --cookie "$cookie_jar" \
    --location --output "$dashboard_page" --write-out '%{http_code}')"
if [[ "$dashboard_status" != "200" ]] || ! grep -Fq 'action="/logout"' "$dashboard_page"; then
    echo "Authenticated safe-read smoke test failed." >&2
    exit 1
fi

echo "Health, login and authenticated safe-read smoke checks passed."
