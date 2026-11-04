"""Generic Git Data API push for codeiva11/SSffmpegVideoOperation.
Compares the working tree against the API base tree of <branch> by blob hash,
creates blobs for changed/added files, posts a partial tree (deletions as sha:null),
commits, and updates the branch ref (no force).
Usage: python3 gitdb_push.py <branch> "<commit message>" [--repo-dir PATH]
Requires: dynamic_credentials (custom.github-api PAT).
"""
import sys, os, json, base64, subprocess
sys.path.insert(0, "/opt/muse/skills/custom-connector-setup/bin")
from dynamic_credentials import add_surrogate_to_request
import urllib.request, urllib.error

OWNER, REPO = "codeiva11", "SSffmpegVideoOperation"

def api(method, path, body=None):
    r = urllib.request.Request("https://api.github.com" + path, method=method,
        data=json.dumps(body).encode() if body is not None else None)
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("Content-Type", "application/json")
    add_surrogate_to_request(r, "custom.github-api", allowed_hosts=["api.github.com"])
    try:
        with urllib.request.urlopen(r) as resp:
            return json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        raise SystemExit(f"API {method} {path} -> {e.code}: {e.read()[:500].decode(errors='replace')}")

def sh(cmd, cwd):
    return subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True, text=True, check=True).stdout.strip()

def main():
    branch, message = sys.argv[1], sys.argv[2]
    repo_dir = sys.argv[4] if len(sys.argv) > 4 and sys.argv[3] == "--repo-dir" else os.getcwd()
    head = api("GET", f"/repos/{OWNER}/{REPO}/git/refs/heads/{branch}")
    base_sha = head["object"]["sha"]
    base_commit = api("GET", f"/repos/{OWNER}/{REPO}/git/commits/{base_sha}")
    base_tree_sha = base_commit["tree"]["sha"]
    tree = api("GET", f"/repos/{OWNER}/{REPO}/git/trees/{base_tree_sha}?recursive=1")
    api_files = {t["path"]: t["sha"] for t in tree.get("tree", []) if t["type"] == "blob"}
    # working tree files (excluding .git)
    disk = {}
    for root, dirs, files in os.walk(repo_dir):
        if ".git" in dirs: dirs.remove(".git")
        for f in files:
            p = os.path.relpath(os.path.join(root, f), repo_dir)
            disk[p] = sh(f"git hash-object -- {shlex_quote(p)}", repo_dir)
    changed, deleted = [], []
    for p, h in disk.items():
        if api_files.get(p) != h: changed.append(p)
    for p in api_files:
        if p not in disk: deleted.append(p)
    print(f"base {base_sha[:8]} | changed/added: {len(changed)} | deleted: {len(deleted)}")
    if not changed and not deleted:
        print("nothing to push"); return
    entries = []
    for p in changed:
        with open(os.path.join(repo_dir, p), "rb") as fh: content = fh.read()
        blob = api("POST", f"/repos/{OWNER}/{REPO}/git/blobs",
                   {"content": base64.b64encode(content).decode(), "encoding": "base64"})
        entries.append({"path": p, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    for p in deleted:
        entries.append({"path": p, "mode": "100644", "type": "blob", "sha": None})
    new_tree = api("POST", f"/repos/{OWNER}/{REPO}/git/trees",
                   {"base_tree": base_tree_sha, "tree": entries})
    commit = api("POST", f"/repos/{OWNER}/{REPO}/git/commits",
                 {"message": message, "tree": new_tree["sha"], "parents": [base_sha]})
    api("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/{branch}", {"sha": commit["sha"], "force": False})
    print("pushed:", commit["sha"], "|", message.splitlines()[0])
    print(f"files: +{len(changed)} -{len(deleted)}")

def shlex_quote(s):
    import shlex; return shlex.quote(s)

if __name__ == "__main__":
    main()
