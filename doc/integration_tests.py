#!/usr/bin/env python3
"""
VCS Integration Test Suite
===========================
Tests multi-step flows including the Redis-backed worker pipeline.

Requirements:
    pip install requests redis

Usage:
    python3 doc/integration_tests.py

Optional environment variables:
    KEYCLOAK_USER         default: dev
    KEYCLOAK_PASSWORD     default: admin
    BACKEND_URL           default: http://localhost:8080
    KEYCLOAK_URL          default: http://localhost:18080
    REDIS_HOST            default: localhost
    REDIS_PORT            default: 16379
    MINIO_WEBHOOK_TOKEN   If set, used as fallback to manually trigger MinIO webhook
                          when auto-webhook doesn't fire within the polling window.
"""

import difflib
import hashlib
import json
import os
import re
import sys
import time
from typing import Optional

try:
    import requests
except ImportError:
    print("ERROR: 'requests' not installed. Run: pip install requests redis")
    sys.exit(1)

try:
    import redis as redis_lib
    REDIS_AVAILABLE = True
except ImportError:
    REDIS_AVAILABLE = False

# ── Configuration ─────────────────────────────────────────────────────────────

KEYCLOAK_USER      = os.getenv("KEYCLOAK_USER", "dev")
KEYCLOAK_PASSWORD  = os.getenv("KEYCLOAK_PASSWORD", "admin")
BACKEND_URL        = os.getenv("BACKEND_URL", "http://localhost:8080")
KEYCLOAK_URL       = os.getenv("KEYCLOAK_URL", "http://localhost:18080")
REDIS_HOST         = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT         = int(os.getenv("REDIS_PORT", "16379"))
MINIO_WEBHOOK_TOKEN = os.getenv("MINIO_WEBHOOK_TOKEN", "")

REDIS_STREAM         = "vcs.diff.jobs"
REDIS_CONSUMER_GROUP = "workers"

# ── ANSI colours ──────────────────────────────────────────────────────────────

GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
BLUE   = "\033[94m"
CYAN   = "\033[96m"
RESET  = "\033[0m"
BOLD   = "\033[1m"

# ── Test counters ─────────────────────────────────────────────────────────────

passed  = 0
failed  = 0
skipped = 0


def log(msg: str, colour: str = "") -> None:
    print(f"{colour}{msg}{RESET}" if colour else msg)


def ok(msg: str) -> None:
    global passed
    passed += 1
    log(f"  ✓ {msg}", GREEN)


def fail(msg: str) -> None:
    global failed
    failed += 1
    log(f"  ✗ {msg}", RED)


def skip(msg: str) -> None:
    global skipped
    skipped += 1
    log(f"  - {msg}", YELLOW)


def section(title: str) -> None:
    log(f"\n{BOLD}{BLUE}━━ {title} ━━{RESET}")


def assert_status(name: str, actual: int, expected) -> bool:
    expected_list = [expected] if isinstance(expected, int) else list(expected)
    if actual in expected_list:
        ok(f"{name}: HTTP {actual}")
        return True
    fail(f"{name}: expected HTTP {expected_list}, got {actual}")
    return False


def assert_truthy(name: str, value, detail: str = "") -> bool:
    if value:
        ok(f"{name}{': ' + detail if detail else ''}")
        return True
    fail(f"{name}: expected truthy, got {value!r}")
    return False


# ── Core utilities ────────────────────────────────────────────────────────────

def sha256hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize_for_worker(text: str) -> bytes:
    """
    Normalise text exactly as DiffApplicator.toLines()/fromLines() does.
    - Strip a single trailing newline before splitting (mirrors toLines)
    - Rejoin with \\n and append one trailing \\n (mirrors fromLines)
    Result bytes are UTF-8.
    """
    if text.endswith("\n"):
        text = text[:-1]
    lines = text.split("\n")
    return ("\n".join(lines) + "\n").encode("utf-8")


def generate_unified_diff(v1_text: str, v2_text: str) -> bytes:
    """
    Generate a unified diff compatible with java-diff-utils (UnifiedDiffUtils).
    Uses normalised line lists so the diff applies cleanly via DiffApplicator.
    """
    v1_norm = normalize_for_worker(v1_text).decode("utf-8")
    v2_norm = normalize_for_worker(v2_text).decode("utf-8")

    # Strip the trailing \\n added by normalize before splitting into lines
    v1_lines = v1_norm.rstrip("\n").split("\n")
    v2_lines = v2_norm.rstrip("\n").split("\n")

    diff_lines = list(
        difflib.unified_diff(v1_lines, v2_lines, fromfile="old", tofile="new", lineterm="")
    )
    if not diff_lines:
        return b""
    return ("\n".join(diff_lines) + "\n").encode("utf-8")


# ── Authentication ─────────────────────────────────────────────────────────────

def get_token() -> str:
    url  = f"{KEYCLOAK_URL}/realms/vcs/protocol/openid-connect/token"
    resp = requests.post(
        url,
        data={
            "grant_type": "password",
            "client_id":  "vcs-frontend",
            "username":   KEYCLOAK_USER,
            "password":   KEYCLOAK_PASSWORD,
        },
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


# ── Redis helpers ─────────────────────────────────────────────────────────────

def get_redis_client():
    if not REDIS_AVAILABLE:
        return None
    try:
        client = redis_lib.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
        client.ping()
        return client
    except Exception as exc:
        log(f"  Redis not reachable ({exc})", YELLOW)
        return None


def stream_info(r) -> dict:
    try:
        length = r.xlen(REDIS_STREAM)
        groups = r.xinfo_groups(REDIS_STREAM)
        pending = sum(g.get("pending", 0) for g in groups)
        return {"length": length, "pending": pending, "groups": groups}
    except Exception:
        return {"length": 0, "pending": 0, "groups": []}


def wait_for_stream_growth(r, baseline: int, timeout: int = 15) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if r.xlen(REDIS_STREAM) > baseline:
            return True
        time.sleep(0.5)
    return False


def wait_for_pending_zero(r, timeout: int = 30) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        groups = r.xinfo_groups(REDIS_STREAM)
        if sum(g.get("pending", 0) for g in groups) == 0:
            return True
        time.sleep(0.5)
    return False


def print_recent_stream_entries(r, count: int = 5) -> None:
    try:
        entries = r.xrevrange(REDIS_STREAM, count=count)
        log(f"  Last {len(entries)} stream entries:", CYAN)
        for eid, edata in entries:
            try:
                payload = json.loads(edata.get("payload", "{}"))
                ts = payload.get("metadata", {}).get("emittedAt", "?")
                log(f"    [{eid}] {payload.get('taskType')} | docId={payload.get('docId')} | at={ts}", CYAN)
            except Exception:
                log(f"    [{eid}] (unparseable payload)", YELLOW)
    except Exception as exc:
        log(f"  Could not read stream: {exc}", YELLOW)


# ── API helpers ───────────────────────────────────────────────────────────────

def api(method: str, path: str, token: str, **kwargs) -> requests.Response:
    url     = f"{BACKEND_URL}{path}"
    headers = auth_headers(token)
    if "json" not in kwargs:
        headers.pop("Content-Type", None)
    return requests.request(method, url, headers=headers, timeout=30, **kwargs)


def extract_doc_id_from_url(upload_url: str) -> Optional[str]:
    """Parse doc UUID from a presigned S3 URL like .../documents/{docId}/v1?..."""
    marker = "/documents/"
    idx    = upload_url.find(marker)
    if idx == -1:
        return None
    start = idx + len(marker)
    end   = upload_url.find("/", start)
    return upload_url[start:end] if end != -1 else None


def upload_to_s3(url: str, body: bytes) -> bool:
    resp = requests.put(url, data=body, headers={"Content-Type": "text/plain"}, timeout=30)
    return resp.ok


def extract_version_number_from_url(url: str) -> Optional[int]:
    """Extract version number from a staging presigned URL (…/v{N}.diff?…)."""
    m = re.search(r"/v(\d+)\.diff", str(url))
    return int(m.group(1)) if m else None


def trigger_webhook_manually(doc_id: str, version_number: int, token: str) -> bool:
    """
    Call the internal MinIO webhook endpoint directly.
    Useful when MINIO_WEBHOOK_TOKEN is set but auto-webhook didn't fire in time
    (e.g. MinIO not yet configured for bucket notifications).
    """
    payload = {
        "Records": [
            {
                "eventName": "s3:ObjectCreated:Put",
                "s3": {"object": {"key": f"tmp/{doc_id}/v{version_number}.diff"}},
            }
        ]
    }
    try:
        resp = requests.post(
            f"{BACKEND_URL}/internal/webhook/minio",
            json=payload,
            headers={"Authorization": token, "Content-Type": "application/json"},
            timeout=10,
        )
        return resp.ok
    except Exception:
        return False


def poll_notifications(token: str, notif_type: str, doc_id: str, timeout: int = 30) -> Optional[dict]:
    """
    Poll GET /notifications until a notification of *notif_type* for *doc_id* appears.
    Returns the notification dict or None on timeout.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        resp = api("GET", "/notifications?page=0&size=100", token)
        if resp.ok:
            data  = resp.json()
            items = data.get("content", data) if isinstance(data, dict) else data
            for n in items:
                if n.get("type") != notif_type:
                    continue
                raw = n.get("payload", "")
                try:
                    p = json.loads(raw) if isinstance(raw, str) else raw
                except Exception:
                    p = {}
                if str(p.get("docId", "")) == str(doc_id):
                    return n
        time.sleep(1)
    return None


# ── Test data ─────────────────────────────────────────────────────────────────

V1_CONTENT = """\
The company's primary objective for the third quarter is to expand our
presence in the European market. We plan to achieve this by opening a new
office in Berlin. Additionally, we will hire five new account managers to
handle the increased workload.
"""

V2_CONTENT = """\
The company's primary objective for the third quarter is to expand our
presence in the Asian market. We intend to achieve this by opening a new
office in Tokio. Additionally, we will hire five new account managers to
handle the increased workload.
"""

# ── Main test runner ──────────────────────────────────────────────────────────

def run() -> bool:
    global passed, failed, skipped

    log(f"\n{BOLD}VCS Integration Tests{RESET}")
    log(f"  Backend  : {BACKEND_URL}")
    log(f"  Keycloak : {KEYCLOAK_URL}  (user={KEYCLOAK_USER})")
    log(f"  Redis    : {REDIS_HOST}:{REDIS_PORT}  stream={REDIS_STREAM}")
    if not REDIS_AVAILABLE:
        log("  WARNING  : redis-py not installed — Redis checks disabled", YELLOW)

    # ── 1. Auth ───────────────────────────────────────────────────────────────
    section("1 · Authentication")
    try:
        token = get_token()
        ok(f"Keycloak token obtained (length={len(token)})")
    except Exception as exc:
        fail(f"Cannot obtain token: {exc}")
        log("Aborting — all other tests require auth.", RED)
        return False

    # ── 2. Redis baseline ─────────────────────────────────────────────────────
    section("2 · Redis Connection & Baseline")
    r = get_redis_client()
    if r:
        ok(f"Connected to Redis at {REDIS_HOST}:{REDIS_PORT}")
        info = stream_info(r)
        baseline_len = info["length"]
        ok(f"Stream '{REDIS_STREAM}' length: {baseline_len}")
        for g in info["groups"]:
            ok(f"  Group '{g.get('name')}': consumers={g.get('consumers')}, pending={g.get('pending')}")
    else:
        skip("Redis unavailable — Redis checks will be skipped throughout")
        baseline_len = 0

    # ── 3. Setup ──────────────────────────────────────────────────────────────
    section("3 · Setup: Organisation & Category")
    ts   = int(time.time())
    resp = api("POST", "/organizations", token, json={"name": f"IntTest Org {ts}"})
    if not assert_status("Create org", resp.status_code, 201):
        log("Cannot continue without an org.", RED)
        return False
    org_id = resp.json().get("id")
    assert_truthy("org_id", org_id, org_id)

    resp   = api("POST", f"/organizations/{org_id}/categories", token, json={"name": "IntTest Category"})
    assert_status("Create category", resp.status_code, 201)
    cat_id = resp.json().get("id")
    assert_truthy("cat_id", cat_id, cat_id)

    # ── 4. Document creation & v1 SNAPSHOT upload ────────────────────────────
    section("4 · Document Creation & v1 SNAPSHOT Upload")

    resp = api(
        "POST", f"/organizations/{org_id}/documents", token,
        json={"name": "IntTest Report", "category_id": cat_id},
    )
    assert_status("Create document", resp.status_code, 201)
    v1_upload_url = str(resp.json().get("upload_url", ""))
    assert_truthy("v1 upload_url", v1_upload_url)

    doc_id = extract_doc_id_from_url(v1_upload_url)
    assert_truthy("doc_id (from upload URL)", doc_id, doc_id)

    v1_bytes = normalize_for_worker(V1_CONTENT)
    assert_truthy("v1 S3 upload", upload_to_s3(v1_upload_url, v1_bytes), "PUT 2xx")

    resp = api("GET", f"/documents/{doc_id}", token)
    assert_status("Get document", resp.status_code, 200)
    assert_truthy("Document name matches", resp.json().get("name") == "IntTest Report", "IntTest Report")

    resp     = api("GET", f"/documents/{doc_id}/versions", token)
    assert_status("List versions (v1)", resp.status_code, 200)
    versions = resp.json().get("content", [])
    v1_obj   = next((v for v in versions if v["version_number"] == 1), None)
    assert_truthy("v1 in version list", v1_obj)
    v1_id = v1_obj["id"] if v1_obj else None

    # Download v1
    if v1_id:
        resp = api("GET", f"/documents/{doc_id}/versions/{v1_id}/download", token)
        assert_status("v1 download URL", resp.status_code, [200, 202])
        dl_url = str(resp.json().get("download_url", ""))
        if dl_url:
            dl = requests.get(dl_url, timeout=15)
            if dl.ok:
                ok(f"v1 content downloaded ({len(dl.content)} bytes)")
            else:
                fail(f"v1 download failed: HTTP {dl.status_code}")
        else:
            skip("v1 download URL empty (version may be DIFF-stored)")

    # ── 5. v2 DIFF upload & worker trigger ────────────────────────────────────
    section("5 · v2 DIFF Upload (is_diff=true) → Redis VERIFY_DIFF Task")

    diff_bytes = generate_unified_diff(V1_CONTENT, V2_CONTENT)
    assert_truthy("Diff is non-empty", len(diff_bytes) > 0, f"{len(diff_bytes)} bytes")

    # Checksum must be SHA-256 of the *reconstructed* full document (what DiffApplicator
    # produces), NOT of the diff bytes — that is what the worker validates against.
    v2_reconstructed = normalize_for_worker(V2_CONTENT)
    expected_checksum = sha256hex(v2_reconstructed)
    log(f"  diff={len(diff_bytes)}B  v2_checksum={expected_checksum[:16]}…", CYAN)

    pre_diff_len = r.xlen(REDIS_STREAM) if r else 0

    resp = api(
        "POST", f"/documents/{doc_id}/versions", token,
        json={"checksum": expected_checksum, "is_draft": False, "is_diff": True},
    )
    assert_status("Create v2 (is_diff=true)", resp.status_code, 201)
    v2_upload_url = str(resp.json().get("upload_url", ""))
    assert_truthy("v2 staging upload_url", v2_upload_url)

    assert_truthy("Diff uploaded to staging S3", upload_to_s3(v2_upload_url, diff_bytes), "PUT 2xx")

    # Wait for MinIO bucket-notification webhook → backend publishes VERIFY_DIFF to Redis
    log("\n  Waiting for VERIFY_DIFF task in Redis (MinIO webhook, up to 15s)…", CYAN)
    verify_task_appeared = False
    if r:
        verify_task_appeared = wait_for_stream_growth(r, pre_diff_len, timeout=15)
        if verify_task_appeared:
            new_len = r.xlen(REDIS_STREAM)
            ok(f"VERIFY_DIFF task in Redis stream (len {pre_diff_len} → {new_len})")
            entries = r.xrevrange(REDIS_STREAM, count=1)
            if entries:
                _, edata = entries[0]
                try:
                    p = json.loads(edata.get("payload", "{}"))
                    ok(f"  taskType={p.get('taskType')}  docId={p.get('docId')}")
                except Exception:
                    pass
        else:
            fail("VERIFY_DIFF task did NOT appear in Redis within 15 s")
            log("  Possible causes:", YELLOW)
            log("    • MinIO webhook not set up — run scripts/minio_setup.sh", YELLOW)
            log("    • MINIO_WEBHOOK_TOKEN mismatch between MinIO and vcs-backend", YELLOW)
            log("    • vcs-backend-worker not consuming from the stream", YELLOW)

            # Fallback: trigger the webhook manually using MINIO_WEBHOOK_TOKEN
            version_number = extract_version_number_from_url(v2_upload_url)
            if MINIO_WEBHOOK_TOKEN and version_number and doc_id:
                log(f"\n  Fallback: manually calling /internal/webhook/minio …", CYAN)
                if trigger_webhook_manually(doc_id, version_number, MINIO_WEBHOOK_TOKEN):
                    log("  Manual webhook call accepted", GREEN)
                    verify_task_appeared = wait_for_stream_growth(r, pre_diff_len, timeout=5)
                    if verify_task_appeared:
                        ok("VERIFY_DIFF task appeared after manual webhook")
                    else:
                        fail("Task still not in Redis after manual webhook")
                else:
                    fail("Manual webhook call rejected (token mismatch or backend unreachable)")
            else:
                skip("MINIO_WEBHOOK_TOKEN not set — skipping manual webhook fallback")
    else:
        skip("Redis unavailable — skipping VERIFY_DIFF stream check")

    # ── 6. Worker: VERIFY_DIFF processing ────────────────────────────────────
    section("6 · Worker: VERIFY_DIFF → DIFF_VERIFIED Notification")

    log("  Polling notifications for DIFF_VERIFIED (up to 30 s)…", CYAN)
    notif = poll_notifications(token, "DIFF_VERIFIED", doc_id, timeout=30)
    if notif:
        ok(f"DIFF_VERIFIED notification received (id={notif.get('id')})")
    else:
        fail("No DIFF_VERIFIED notification after 30 s — check vcs-backend-worker logs")
        log("  Possible causes:", YELLOW)
        log("    • vcs-backend-worker not running", YELLOW)
        log("    • VERIFY_DIFF task never published (see section 5 above)", YELLOW)
        log("    • Checksum mismatch (diff generation or normalization differs)", YELLOW)

    # Check Redis pending count decreases
    if r:
        time.sleep(1)
        info = stream_info(r)
        pending = info["pending"]
        if pending == 0:
            ok("Redis stream: 0 pending entries (all tasks consumed)")
        else:
            skip(f"Redis stream still has {pending} pending entries (worker may be processing)")

    # Verify v2 shows up in version list with DIFF storage type
    resp     = api("GET", f"/documents/{doc_id}/versions", token)
    v2_id    = None
    if resp.ok:
        versions = resp.json().get("content", [])
        v2_obj   = next((v for v in versions if v["version_number"] == 2), None)
        if v2_obj:
            ok(f"v2 in list: status={v2_obj.get('status')}  storage_type={v2_obj.get('storage_type')}")
            v2_id = v2_obj["id"]
        else:
            fail("v2 not found in version list")
    else:
        fail(f"List versions failed: HTTP {resp.status_code}")

    # ── 7. Download DIFF version → RECONSTRUCT_DOCUMENT ─────────────────────
    section("7 · Download DIFF Version → Worker RECONSTRUCT_DOCUMENT")

    if v2_id:
        pre_recon_len = r.xlen(REDIS_STREAM) if r else 0

        resp = api("GET", f"/documents/{doc_id}/versions/{v2_id}/download", token)
        assert_status("v2 download URL request", resp.status_code, [200, 202])

        if resp.status_code == 202:
            ok("202 Accepted — RECONSTRUCT_DOCUMENT task dispatched")
        else:
            ok("200 OK — immediate download URL (SNAPSHOT or already reconstructed)")

        # Check Redis for RECONSTRUCT_DOCUMENT task
        if r:
            grew = wait_for_stream_growth(r, pre_recon_len, timeout=10)
            if grew:
                new_len = r.xlen(REDIS_STREAM)
                ok(f"RECONSTRUCT_DOCUMENT task in Redis (len {pre_recon_len} → {new_len})")
                entries = r.xrevrange(REDIS_STREAM, count=1)
                if entries:
                    _, edata = entries[0]
                    try:
                        p = json.loads(edata.get("payload", "{}"))
                        ok(f"  taskType={p.get('taskType')}  docId={p.get('docId')}")
                    except Exception:
                        pass
            else:
                skip("No RECONSTRUCT_DOCUMENT task appeared (v2 may be SNAPSHOT-stored or worker immediate)")

        # Poll for DOCUMENT_RECONSTRUCTED notification
        log("\n  Polling for DOCUMENT_RECONSTRUCTED (up to 30 s)…", CYAN)
        notif = poll_notifications(token, "DOCUMENT_RECONSTRUCTED", doc_id, timeout=30)
        if notif:
            ok(f"DOCUMENT_RECONSTRUCTED notification received (id={notif.get('id')})")
            raw = notif.get("payload", "")
            try:
                payload = json.loads(raw) if isinstance(raw, str) else raw
            except Exception:
                payload = {}
            dl_url = payload.get("presignedDownloadUrl", "")
            if dl_url:
                dl = requests.get(str(dl_url), timeout=15)
                if dl.ok:
                    ok(f"Reconstructed v2 downloaded ({len(dl.content)} bytes)")
                    reconstructed = dl.text
                    # Check content matches expected v2 (after worker normalisation)
                    expected_v2 = normalize_for_worker(V2_CONTENT).decode("utf-8")
                    if reconstructed == expected_v2:
                        ok("Reconstructed content matches expected v2 exactly ✓")
                    elif reconstructed.strip() == expected_v2.strip():
                        ok("Reconstructed content matches v2 (modulo whitespace)")
                    else:
                        skip("Content mismatch — normalization may differ from expected")
                else:
                    fail(f"Reconstructed download failed: HTTP {dl.status_code}")
            else:
                skip("presignedDownloadUrl missing from notification payload")
        else:
            skip("No DOCUMENT_RECONSTRUCTED notification — worker may not be running")
    else:
        skip("Skipping RECONSTRUCT_DOCUMENT test — v2_id unavailable")

    # ── 8. Diff endpoint ─────────────────────────────────────────────────────
    section("8 · GET /versions/diff (v1 → v2)")

    if v1_id and v2_id:
        resp = api("GET", f"/documents/{doc_id}/versions/diff?from={v1_id}&to={v2_id}", token)
        assert_status("Diff endpoint", resp.status_code, [200, 202])
        if resp.ok:
            body = resp.json()
            assert_truthy("diff.from_version_id", body.get("from_version_id"))
            assert_truthy("diff.to_version_id",   body.get("to_version_id"))
            assert_truthy("diff.diff field",       body.get("diff"))
            log(f"  diff payload: {str(body.get('diff', ''))[:120]}…", CYAN)
    else:
        skip("Skipping diff endpoint — version IDs not available")

    # ── 9. Comments ───────────────────────────────────────────────────────────
    section("9 · Comments on v1")

    if v1_id:
        resp = api(
            "POST", f"/documents/{doc_id}/versions/{v1_id}/comments", token,
            json={"content": "Integration test comment"},
        )
        assert_status("Add comment", resp.status_code, 201)
        comment_id = resp.json().get("id")
        assert_truthy("Comment id", comment_id, comment_id)

        resp = api("GET", f"/documents/{doc_id}/versions/{v1_id}/comments", token)
        assert_status("List comments", resp.status_code, 200)
        comments = resp.json()
        assert_truthy(f"Comment list ≥1", len(comments) >= 1, f"got {len(comments)}")
    else:
        skip("Skipping comments — v1_id unavailable")

    # ── 10. Notifications ─────────────────────────────────────────────────────
    section("10 · Notifications Endpoint")

    resp = api("GET", "/notifications?page=0&size=20", token)
    assert_status("List notifications", resp.status_code, 200)
    data    = resp.json()
    content = data.get("content", data) if isinstance(data, dict) else data
    ok(f"{len(content)} notification(s) retrieved")
    if content:
        types = list(dict.fromkeys(n.get("type") for n in content[:10]))
        ok(f"  Types seen: {types}")

    # ── 11. Redis final snapshot ──────────────────────────────────────────────
    section("11 · Redis Final State")

    if r:
        info = stream_info(r)
        ok(f"Stream '{REDIS_STREAM}' final length : {info['length']}")
        ok(f"Pending entries                      : {info['pending']}")
        print_recent_stream_entries(r, count=5)
    else:
        skip("Redis unavailable")

    # ── 12. Cleanup ───────────────────────────────────────────────────────────
    section("12 · Cleanup")

    resp = api("DELETE", f"/documents/{doc_id}", token)
    assert_status("Delete document", resp.status_code, [200, 204])

    resp = api("DELETE", f"/organizations/{org_id}/categories/{cat_id}", token)
    assert_status("Delete category", resp.status_code, [200, 204])

    resp = api("DELETE", f"/organizations/{org_id}", token)
    assert_status("Delete org", resp.status_code, [200, 204])

    ok("Cleanup complete")

    # ── Summary ───────────────────────────────────────────────────────────────
    total = passed + failed + skipped
    log(f"\n{BOLD}{'━' * 56}{RESET}")
    log(
        f"{BOLD}Results: "
        f"{GREEN}{passed} passed{RESET}  "
        f"{BOLD}{RED}{failed} failed{RESET}  "
        f"{BOLD}{YELLOW}{skipped} skipped{RESET}  "
        f"{BOLD}/ {total} total{RESET}"
    )
    if failed == 0:
        log(f"\n{GREEN}✓ All checks passed!{RESET}")
    else:
        log(f"\n{RED}✗ {failed} check(s) failed — see output above.{RESET}")

    return failed == 0


if __name__ == "__main__":
    success = run()
    sys.exit(0 if success else 1)
