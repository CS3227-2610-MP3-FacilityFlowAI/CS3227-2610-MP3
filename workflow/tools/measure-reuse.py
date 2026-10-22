"""Measure the working tree against the fixed MP2 baseline without changing either repo.

Exact normalized-file matches corroborate provenance; they do not detect every
partial adaptation. The reviewed ledger remains authoritative for conceptual reuse.
Only tracked/non-ignored application, test, documentation and wrapper paths are read.
"""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

BASELINE = "9819b857d880134c1fc1ffef8a8bcd5d6e536f43"
WRAPPERS = {"gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties"}
# Exclude the measurement/acceptance records themselves to avoid self-counting.
EXCLUDED = {"workflow/REUSE.md", "docs/Acceptance.md"}


def git(repo, *args):
    return subprocess.check_output(
        ["git", "-c", f"safe.directory={repo.as_posix()}", "-C", str(repo), *args])


def normalized(data):
    return data.replace(b"\r\n", b"\n")


def category(name):
    if name in EXCLUDED:
        return None
    if name in WRAPPERS:
        return "build bootstrap"
    if name.startswith("src/main/"):
        return "application source and resources"
    if name.startswith("src/test/"):
        return "tests and fixtures"
    if name.endswith(".md") and (name.startswith(("docs/", "workflow/"))
                                or name in {"README.md", "AGENTS.md", "CONTEXT.md"}):
        return "project documentation (excluding historical logs)"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mp2", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    mp2 = args.mp2.resolve()
    # Read baseline blobs in one batch; no checkout, reset or modification of MP2.
    tree = git(mp2, "ls-tree", "-r", "-z", BASELINE)
    entries = []
    for entry in tree.split(b"\0"):
        if entry:
            metadata, name = entry.split(b"\t", 1)
            mode, kind, object_id = metadata.decode().split()
            if kind == "blob":
                entries.append((name.decode(), object_id))
    query = "".join(object_id + "\n" for _, object_id in entries).encode()
    stream = subprocess.run(
        ["git", "-c", f"safe.directory={mp2.as_posix()}", "-C", str(mp2),
         "cat-file", "--batch"], input=query, capture_output=True, check=True).stdout
    originals = {}
    offset = 0
    for name, _ in entries:
        end = stream.index(b"\n", offset)
        size = int(stream[offset:end].split()[-1])
        data = stream[end + 1:end + 1 + size]
        offset = end + size + 2
        originals.setdefault(hashlib.sha256(normalized(data)).hexdigest(), []).append(name)
    names = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
    groups = {}
    matches = []
    for name in sorted({value.decode() for value in names.split(b"\0") if value}):
        group = category(name)
        path = root / name
        if group is None or not path.is_file():
            continue
        data = path.read_bytes()
        try:
            text = data.decode("utf-8-sig") if b"\0" not in data else None
        except UnicodeDecodeError:
            text = None
        stats = groups.setdefault(group, {"files": 0, "text_files": 0,
                                           "text_lines": 0, "binary_files": 0})
        stats["files"] += 1
        stats["binary_files" if text is None else "text_files"] += 1
        if text is not None:
            stats["text_lines"] += len(text.splitlines())
        sources = originals.get(hashlib.sha256(normalized(data)).hexdigest(), [])
        if sources:
            matches.append({"mp3": name, "mp2": sources,
                            "lines": len(text.splitlines()) if text is not None else None})
    result = {"mp2_baseline": BASELINE, "mp3_source_revision": git(root, "rev-parse", "HEAD").decode().strip(),
              "scope": "current working tree; CRLF/LF-normalized exact-file comparison",
              "excluded": sorted(EXCLUDED), "groups": groups, "exact_matches": matches,
              "limits": "Exact files only; conceptual and partial reuse require the reviewed ledger. No copied-line percentage inferred."}
    output = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    print(output, end="")


if __name__ == "__main__":
    main()
