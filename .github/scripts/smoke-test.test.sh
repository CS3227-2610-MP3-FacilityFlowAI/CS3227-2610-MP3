#!/usr/bin/env bash
# NFR-DEP-001: rejected credentials must not pass the release smoke gate.
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

cat > "$work_dir/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
output=''
status=''
url=''
while (($#)); do
    case "$1" in
        --output) output="$2"; shift ;;
        --write-out) status=200; shift ;;
        https://*) url="$1" ;;
    esac
    shift
done
case "$url" in
    */health) printf '{"status":"UP"}' > "$output" ;;
    */login)
        if [[ -n "$status" ]]; then
            status=302
        else
            printf '<input name="_csrf" value="synthetic-token">' > "$output"
        fi ;;
    */)
        if [[ "$SMOKE_TEST_AUTHENTICATED" == true ]]; then
            printf '<form method="post" action="/logout">My workspace</form>' > "$output"
        else
            printf '<p>Sign in to your maintenance workspace.</p>' > "$output"
        fi ;;
    *) exit 2 ;;
esac
printf '%s' "$status"
MOCK
chmod +x "$work_dir/curl"
export PATH="$work_dir:$PATH" SMOKE_USERNAME=synthetic SMOKE_PASSWORD=synthetic
export SMOKE_TEST_AUTHENTICATED=false
if bash "$script_dir/smoke-test.sh" https://synthetic.vercel.app > "$work_dir/result" 2>&1; then
    echo 'FAIL: unauthenticated login page passed the release gate' >&2
    exit 1
fi
grep -Fq 'Authenticated safe-read smoke test failed.' "$work_dir/result"
export SMOKE_TEST_AUTHENTICATED=true
bash "$script_dir/smoke-test.sh" https://synthetic.vercel.app
echo 'NFR-DEP-001: authenticated smoke success and rejected-login regression passed'
