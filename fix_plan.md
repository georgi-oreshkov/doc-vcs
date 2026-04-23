# Fix Plan

Each issue is diagnosed, located to exact files/lines, and described with a precise fix. Fixes are ordered so no fix assumes another is already in place, and none of them overlap or interfere.

---

## Issue 1 — Review requests leak to unassigned reviewers

**Root cause:** `RequestService.listRequests()` (line 111–117) filters visibility by org-level role only. Any user with `REVIEWER` role in the org can see ALL review requests for ALL documents in that org, regardless of which documents they are actually assigned to.

**Fix — `vcs-backend/.../request/service/RequestService.java`, method `listRequests()`:**
```
Change the filter predicate from:
  "return if caller is ADMIN or REVIEWER in the doc's org"
To:
  "return if caller is ADMIN in the doc's org,
   OR if caller is a REVIEWER who is in the document's reviewer list"
```
Concretely, replace the `organizationFacade.hasRole(orgId, callerId, "ADMIN", "REVIEWER")` check with:
- `organizationFacade.hasRole(orgId, callerId, "ADMIN")` grants full access
- `organizationFacade.hasRole(orgId, callerId, "REVIEWER")` AND `documentFacade.getReviewerIds(r.getDocId()).contains(callerId)` grants access only for assigned docs

No other service is touched. `VersionService.listPendingReviewVersions()` already correctly filters by assigned reviewer (`findDocIdsByReviewerId`) and does not need to change.

---

## Issue 2 — Initial version created with contradictory PENDING+isDraft=true status

**Root cause:** `VersionFacade.createInitialVersion()` (line 47) builds the entity with `status = VersionStatus.PENDING` while simultaneously setting `isDraft = true`. These two values contradict each other and cause the initial version to show "Reviewing" in the UI immediately after document creation, before any review is ever requested.

Additionally, `VersionMapper.toEntity()` (line 35) has `@Mapping(target = "status", constant = "PENDING")` as its constant — a misleading default that is immediately overridden in `VersionService.createVersion()` but adds unnecessary confusion.

**Fix 1 — `vcs-backend/.../version/api/VersionFacade.java`, `createInitialVersion()`:**
```
Change VersionStatus.PENDING → VersionStatus.DRAFT
```

**Fix 2 — `vcs-backend/.../version/mapper/VersionMapper.java`, `toEntity()`:**
```
Change @Mapping(target = "status", constant = "PENDING") → constant = "DRAFT"
```
(VersionService.createVersion() already overwrites to DRAFT anyway, so this just makes the mapper consistent.)

---

## Issue 3 — "Reviewing" status appears before a review is requested; rollback skips draft

**Root cause (part a):** Follows directly from Issue 2 — once the initial version is fixed to `DRAFT`, the "Reviewing" badge (shown when `version.status === 'PENDING'` in `DocumentViewerView.jsx` line 252) will no longer appear until `requestReview()` is explicitly called. No additional frontend change is needed.

**Root cause (part b):** `VersionService.rollbackVersion()` (line 264–268) creates the new rollback version directly with `status = PENDING, isDraft = false`, bypassing the draft workflow entirely. A rollback should start as a draft and go through review like any other version.

**Fix — `vcs-backend/.../version/service/VersionService.java`, `rollbackVersion()`:**
```
When building the rollback VersionEntity, set:
  .status(VersionStatus.DRAFT)
  .isDraft(true)
```
Remove the call to `documentFacade.updateStatusToPendingReview(docId)` that follows — the document status should not change until a review is explicitly requested on the rollback version.

---

## Issue 4/5/6 — No UPLOADING flag; race conditions and 404 on in-flight versions

**Root cause:** After `createVersion()` generates a presigned PUT URL, the S3 upload happens client-side asynchronously. The backend has no record of whether the file has actually landed in S3 yet. Other users can request the download URL or trigger a review before the upload is complete, resulting in 404 from S3.

**Fix (4 parts):**

**Part A — `vcs-backend/.../version/domain/VersionEntity.java`:**
Add a new boolean column:
```java
@Column(name = "is_uploading", nullable = false)
@Builder.Default
private Boolean isUploading = true;
```
This is `true` by default so every newly created version starts in the uploading state.

**Part B — `vcs-backend/.../version/api/VersionFacade.java`, `createInitialVersion()`:**
The builder for the initial version should explicitly set `isUploading(true)` (it gets the `@Builder.Default` automatically, but be explicit for clarity).

**Part C — `vcs-backend/.../version/service/VersionService.java`:**
Add guards in three places:

- `requestReview()`: if `version.getIsUploading()` is true, throw `AppException(BAD_REQUEST, "Cannot request review: upload in progress.")`.
- `getDownloadUrl()`: if `version.getIsUploading()` is true, throw `AppException(CONFLICT, "File is still uploading.")`.
- `createVersion()`: before creating a new version, check if the current latest version for the doc has `isUploading=true`; if so, throw `AppException(CONFLICT, "Previous version upload not yet complete.")`.

**Part D — `vcs-backend/.../version/web/MinioWebhookController.java`:**
Add a second key pattern for permanent snapshot uploads:
```
Pattern SNAPSHOT_KEY = Pattern.compile(
  "^documents/([0-9a-f-]{36})/v(\\d+)$"
);
```
When this pattern matches on a `s3:ObjectCreated:*` event, call a new service method `versionService.handleSnapshotUploaded(docId, versionNumber)` that:
  1. Looks up the version by `(docId, versionNumber)`.
  2. Sets `isUploading = false` and saves.
  3. Only do this for `StorageType.SNAPSHOT` versions (diff versions are cleared by the worker after VERIFY_DIFF).

For diff versions, the worker already handles the happy path via `VERIFY_DIFF`. The worker should also set `isUploading = false` on the version when it completes successfully — this is a worker-side change (the worker updates the `versions` row directly or via a callback endpoint).

**Part E — API / frontend:**
Include `isUploading` in the `Version` DTO / OpenAPI schema. On the frontend (`DocumentViewerView.jsx`):
- Disable the **Download** button if `selectedVersion.is_uploading`.
- Disable the **New Version** button if the latest version has `is_uploading`.
- Disable the **Request Review** button if `selectedVersion.is_uploading`.
- Show a small "Uploading…" indicator next to the version label when `is_uploading` is true.

---

## Issue 7 — Rollback preview returns 404; preview should not use S3 directly

**Root cause:** `VersionService.rollbackVersion()` (line 263–268) creates a new `VersionEntity` with `versionNumber = nextNumber` but never copies the actual file in S3. When preview or download tries to fetch `S3KeyTemplates.permanentVersion(docId, nextNumber)`, the key does not exist because the content lives at `permanentVersion(docId, targetVersion.getVersionNumber())`.

**Fix — `vcs-backend/.../version/service/VersionService.java`, `rollbackVersion()`:**

Inject `software.amazon.awssdk.services.s3.S3Client` (or reuse `S3Presigner`'s underlying client) into `VersionService`. After saving the rollback entity, issue an S3 CopyObject request:

```
CopyObjectRequest copy = CopyObjectRequest.builder()
    .sourceBucket(s3Properties.bucket())
    .sourceKey(S3KeyTemplates.permanentVersion(docId, target.getVersionNumber()))
    .destinationBucket(s3Properties.bucket())
    .destinationKey(S3KeyTemplates.permanentVersion(docId, nextNumber))
    .build();
s3Client.copyObject(copy);
```

This makes the file available under the new key immediately. No changes to the S3 key derivation logic, no new entity fields required.

For the "preview should not use S3" concern: the V1 preview in `DocumentViewerView.jsx` (lines 133–156) fetches the raw file from a presigned S3 URL. Once the copy is in place, this will resolve correctly. No separate non-S3 preview path is needed for rollback, because after the copy the key exists and the presigned URL works.

If a non-S3 preview path is still desired in the future (e.g., to avoid presigned URL expiry issues), that is a separate enhancement.

**Note:** `rollbackVersion()` also needs the `isUploading=false` fix described in Issue 3 and should set it to `false` immediately (since the S3 copy is synchronous and completes within the request). Update the rollback builder to `.isUploading(false)` after the copy succeeds.

---

## Issue 8 — Download opens S3 URL in a new tab instead of saving a file

**Root cause:** `DocumentViewerView.jsx`, `handleDownload()` (line 165):
```javascript
window.open(download_url, '_blank');
```
This navigates to the S3 presigned URL in a new tab, which the browser typically renders inline (for text/plain, PDF, etc.) rather than downloading.

**Fix — `vcs-frontend/src/views/DocumentViewerView.jsx`, `handleDownload()`:**

Replace `window.open(download_url, '_blank')` with a programmatic anchor click:
```javascript
const a = document.createElement('a');
a.href = download_url;
a.download = `${doc?.name ?? 'document'}_v${selectedVersion.version_number}`;
document.body.appendChild(a);
a.click();
document.body.removeChild(a);
```
The `download` attribute tells the browser to save the file with the given filename rather than navigating to it.

---

## Issue 9 — Deleting an organization returns HTTP 500, yet the deletion appears to succeed

**Root cause:** `OrganizationService.deleteOrganization()` (line 64–66) calls `organizationRepository.delete(resolve(orgId))` but does not first remove the dependent `org_memberships` and `org_user_roles` rows for the org. If the database has FK constraints on those tables referencing `organizations.id`, the delete fails with a constraint violation that Spring converts to HTTP 500. The "deletion is successful" observation likely comes from the client-side cache being invalidated before the error response is processed, giving the false impression of success.

**Fix — two parts:**

**Part A — add `deleteByOrgId` to both repositories:**

`OrgMembershipRepository.java` — add:
```java
@Modifying
@Transactional
void deleteByOrgId(UUID orgId);
```

`OrgUserRoleRepository.java` — add:
```java
@Modifying
@Transactional
void deleteByOrgId(UUID orgId);
```

**Part B — `OrganizationService.java`, `deleteOrganization()`:**
```java
public void deleteOrganization(UUID orgId) {
    OrganizationEntity org = resolve(orgId);
    orgUserRoleRepository.deleteByOrgId(orgId);
    orgMembershipRepository.deleteByOrgId(orgId);
    organizationRepository.delete(org);
}
```
Roles must be deleted before memberships (if there is a FK from roles to memberships), and memberships before the org row.

**Note:** This does not cascade to `documents` owned by the org. If documents need to be deleted along with the org, that should be a separate, explicit decision (and is outside the scope of this issue).

---

## Issue 10 — Readers can see DRAFT and PENDING_REVIEW documents

**Root cause:** `DocumentService.listDocuments()` (line 70–82) applies the same Specification for all roles. It does not restrict results by document status for READER users, so they see DRAFT and PENDING_REVIEW documents that they should not have visibility into.

**Fix — `vcs-backend/.../document/service/DocumentService.java`, `listDocuments()`:**

The method signature already receives the org-level role implicitly via Spring Security. To apply the restriction, resolve the caller's role and add a status filter when the caller is a READER:

```java
public Page<DocumentEntity> listDocuments(UUID orgId, UUID categoryId, UUID authorId,
                                           String status, String name, int page, int size,
                                           UUID callerId) {
    Specification<DocumentEntity> spec = Specification.where(DocumentSpec.hasOrgId(orgId));
    // ... existing filters ...

    // Readers may only see approved documents
    List<String> callerRoles = orgRoleLookup.findRoles(orgId, callerId);
    boolean isReaderOnly = callerRoles.stream()
        .allMatch(r -> r.equals("READER"));
    if (isReaderOnly) {
        spec = spec.and(DocumentSpec.hasStatus("APPROVED"));
    }

    return documentRepository.findAll(spec, PageRequest.of(page, size));
}
```

Add `OrgRoleLookup` as a dependency to `DocumentService` (it is already used by `VersionService`) and pass `callerId` from the controller. Alternatively, read the current user from `SecurityContextHolder` directly inside the service without changing the method signature (avoids touching the controller).

**Frontend note:** `DocumentsView.jsx` currently shows all documents returned by the API. No frontend change is needed — once the backend filters correctly, READERs will only receive APPROVED documents.

---

## Non-interference checklist

| Issue | Files touched | Conflicts with |
|-------|---------------|---------------|
| 1 | `RequestService.java` | None |
| 2 | `VersionFacade.java`, `VersionMapper.java` | 3 (both fix initial version status — apply together) |
| 3 | `VersionService.java` (`rollbackVersion`) | 7 (rollback status set; apply together with Issue 7) |
| 4/5/6 | `VersionEntity.java`, `VersionService.java`, `MinioWebhookController.java`, `DocumentViewerView.jsx` | 3 (adds `isUploading`; rollback in Issue 3 must set `isUploading=false` after copy) |
| 7 | `VersionService.java` (`rollbackVersion`) | 3 (same method; apply as one combined change to `rollbackVersion`) |
| 8 | `DocumentViewerView.jsx` (`handleDownload`) | None |
| 9 | `OrgMembershipRepository.java`, `OrgUserRoleRepository.java`, `OrganizationService.java` | None |
| 10 | `DocumentService.java` | None |

**Issues 2+3 must be applied together** (both affect initial version creation and rollback status).  
**Issues 3+7 must be applied together** (both modify `rollbackVersion()`; the combined method sets `status=DRAFT, isDraft=true, isUploading=false` and copies the S3 object).  
All other issues are fully independent.
