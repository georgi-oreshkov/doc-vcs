# Fix Plan

Each issue is diagnosed down to the exact file, method, and generated code where the bug lives. Fixes are ordered so that no fix assumes another is already in place and none of them overlap or interfere with each other.

---

## Issue 1 — Category selector and reviewer selector always shows "None"; update does not persist

### Root cause A — `applyUpdate` silently clears all document reviewers on every PATCH

`DocumentMapper.applyUpdate()` has `@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)` and no explicit `@Mapping` for `reviewerIds`. MapStruct's generated implementation (`DocumentMapperImpl.java`) is:

```java
if (entity.getReviewerIds() != null) {
    List<UUID> list = req.getReviewerIds();   // always [] when reviewer_ids is absent from JSON
    if (list != null) {                        // [] != null → condition is true
        entity.getReviewerIds().clear();       // ALL reviewers deleted from DB
        entity.getReviewerIds().addAll(list);  // nothing added back
    }
}
```

When the frontend sends `PATCH /documents/{docId}` with only `{ "category_id": "uuid" }` (no `reviewer_ids`), Jackson deserialises `CreateDocumentRequest` with `reviewerIds = new ArrayList<>()` (the Java field initialiser). That empty list is **not null**, so the IGNORE strategy does not apply. Hibernate then issues `DELETE FROM document_reviewers WHERE document_id = ?`, wiping all reviewer assignments. Because the document's assigned reviewers are gone, subsequent reviewer-facing `@PreAuthorize` checks that rely on `documentFacade.getReviewerIds()` return an empty list and may deny access, producing side-effects that cause the category save to be inadvertently rolled back in certain transaction configurations.

**Fix — `vcs-backend/.../document/mapper/DocumentMapper.java`, `applyUpdate()`:**

Add `@Mapping(target = "reviewerIds", ignore = true)` to the method:

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
@Mapping(target = "id", ignore = true)
@Mapping(target = "orgId", ignore = true)
@Mapping(target = "authorId", ignore = true)
@Mapping(target = "status", ignore = true)
@Mapping(target = "latestVersionId", ignore = true)
@Mapping(target = "latestApprovedVersionId", ignore = true)
@Mapping(target = "reviewerIds", ignore = true)   // ← ADD THIS LINE
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "createdBy", ignore = true)
void applyUpdate(CreateDocumentRequest req, @MappingTarget DocumentEntity entity);
```

Reviewer management must use a dedicated endpoint (e.g., `PUT /documents/{docId}/reviewers`), not piggyback on the general metadata PATCH.

### Root cause B — Clearing the category via the Select (isClearable) does not persist

When the user clears the category selector (selects nothing), `onSelectionChange` gives `keys = Set()`. The handler computes `val = Array.from(keys)[0] ?? null` → `null`. The mutation sends `{ "category_id": null }`. `CreateDocumentRequest.getCategoryId()` returns `null`. The generated `applyUpdate` code is:

```java
if (req.getCategoryId() != null) {   // null → condition is false → entity.categoryId unchanged
    entity.setCategoryId(req.getCategoryId());
}
```

The DB retains the old `category_id`. After refresh the old category reappears — confusing the user into thinking the original "set" operation also failed.

**Fix — `vcs-frontend/src/views/DocumentViewerView.jsx`, `onSelectionChange`:**

Distinguish between "user selected a category" and "user cleared the category" and send the value only when it is genuinely changed:

```jsx
onSelectionChange={(keys) => {
  const val = Array.from(keys)[0] ?? null;
  setSelectedCategoryKeys(val ? new Set([val]) : new Set([]));
  updateDocument.mutate(
    { docId, data: { category_id: val } },
    {
      onSuccess: () => addToast({ title: 'Category updated', color: 'success' }),
      onError: (err) => addToast({ title: 'Category update failed', description: err?.message, color: 'danger' }),
    }
  );
}}
```

**Fix — `vcs-backend/.../document/mapper/DocumentMapper.java`, `applyUpdate()`:**

Change `NullValuePropertyMappingStrategy.IGNORE` to `NullValuePropertyMappingStrategy.SET_TO_NULL` so that an explicit `null` in the request body does clear the field. Add the individual `ignore = true` entries for fields that must never be updated via this method (all of the existing ignores plus `reviewerIds` from Root cause A):

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
@Mapping(target = "id", ignore = true)
@Mapping(target = "orgId", ignore = true)
@Mapping(target = "authorId", ignore = true)
@Mapping(target = "status", ignore = true)
@Mapping(target = "latestVersionId", ignore = true)
@Mapping(target = "latestApprovedVersionId", ignore = true)
@Mapping(target = "reviewerIds", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "createdBy", ignore = true)
void applyUpdate(CreateDocumentRequest req, @MappingTarget DocumentEntity entity);
```

With `SET_TO_NULL`, MapStruct will call `entity.setCategoryId(null)` when `req.getCategoryId()` is null, which propagates correctly to the DB. All explicitly ignored fields are still protected.

### Root cause C — No error feedback when PATCH fails

The `updateDocument.mutate(...)` call in `DocumentViewerView` has no `onError` handler. If the PATCH fails with any server error, the UI optimistically shows the new category (via `setSelectedCategoryKeys`) but never re-fetches; after a browser refresh the old value reappears with no indication that the save failed.

**Fix — `vcs-frontend/src/views/DocumentViewerView.jsx`:**

Add `onError` to the `updateDocument.mutate` call (shown in Root cause B fix above).

---

## Issue 2 — Any author in the org can upload new versions and request reviews on other authors' documents

### Root cause

`VersionService.createVersion()` and `VersionService.requestReview()` are gated with `@PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")`. `isDocumentMember` resolves the document's org and checks whether the caller has **any** role in that org:

```java
public boolean isDocumentMember(UUID docId, Authentication auth) {
    return documentOrgLookup.findOrgId(docId)
        .map(orgId -> !orgRoleLookup.findRoles(orgId, userId).isEmpty())
        .orElse(false);
}
```

Any user with `AUTHOR` role in the org passes this check regardless of which document is targeted.

### Fix — `vcs-backend/.../version/service/VersionService.java`

Add an author-or-admin guard inside **both** `createVersion()` and `requestReview()`, immediately after `documentFacade.requireExists(docId)`:

```java
// In createVersion() and requestReview() — add after requireExists:
UUID docAuthorId = documentFacade.getAuthorId(docId);
UUID orgId = documentFacade.resolveOrgId(docId);
List<String> callerRoles = orgRoleLookup.findRoles(orgId, callerId);
if (!docAuthorId.equals(callerId) && !callerRoles.contains("ADMIN")) {
    throw new AppException(HttpStatus.FORBIDDEN,
        "Only the document author or an org admin may perform this action.");
}
```

No changes to `@PreAuthorize` (it correctly blocks non-members); the extra in-method check narrows the permitted callers to author + admin.

---

## Issue 3 — Reviewers can approve or reject documents they are not assigned to

### Root cause — `approveVersion` / `rejectVersion`

`VersionService.approveVersion()` and `rejectVersion()` each call:

```java
requireRole(orgId, callerId, "REVIEWER", "ADMIN");
```

`requireRole` only checks whether the caller has the `REVIEWER` or `ADMIN` role anywhere in the org. It does **not** consult the document's `reviewerIds` list, so any org-level reviewer can approve or reject any document.

### Root cause — `RequestService.actionRequest`

```java
if (!organizationFacade.hasRole(orgId, callerId, "ADMIN", "AUTHOR", "REVIEWER")) {
    throw new AppException(HttpStatus.FORBIDDEN, "Not authorized to action requests");
}
```

This additionally allows `AUTHOR` role holders to approve/reject requests (approve-your-own-work vulnerability), and it allows any `REVIEWER` in the org to action any review request.

### Fix — `vcs-backend/.../version/service/VersionService.java`

Replace the `requireRole(orgId, callerId, "REVIEWER", "ADMIN")` call in **both** `approveVersion()` and `rejectVersion()` with a two-part check:

```java
List<String> callerRoles = orgRoleLookup.findRoles(orgId, callerId);
boolean isAdmin = callerRoles.contains("ADMIN");
boolean isAssignedReviewer = callerRoles.contains("REVIEWER")
    && documentFacade.getReviewerIds(docId).contains(callerId);
if (!isAdmin && !isAssignedReviewer) {
    throw new AppException(HttpStatus.FORBIDDEN,
        "Only an assigned reviewer or an admin may approve/reject this version.");
}
```

### Fix — `vcs-backend/.../request/service/RequestService.java`, `actionRequest()`

Replace the role check:

```java
// OLD:
if (!organizationFacade.hasRole(orgId, callerId, "ADMIN", "AUTHOR", "REVIEWER")) { ... }

// NEW:
List<String> callerRoles = orgRoleLookup.findRoles(orgId, callerId);
boolean isAdmin = callerRoles.contains("ADMIN");
boolean isAssignedReviewer = callerRoles.contains("REVIEWER")
    && documentFacade.getReviewerIds(request.getDocId()).contains(callerId);
if (!isAdmin && !isAssignedReviewer) {
    throw new AppException(HttpStatus.FORBIDDEN,
        "Only an assigned reviewer or an admin may action this request.");
}
```

This removes the `AUTHOR` permission (authors should not self-approve) and restricts `REVIEWER` to only assigned ones.

---

## Issue 4 — Rollback preview is empty; rollback broken when rolling back to a DIFF version

### Root cause A — `storageType` is incorrectly inherited from the target version

`rollbackVersion()` copies the entity fields from the target version including `storageType`:

```java
VersionEntity rollback = versionRepository.save(VersionEntity.builder()
    .docId(docId).versionNumber(nextNumber).status(VersionStatus.DRAFT).isDraft(true)
    .isUploading(false).checksum(target.getChecksum())
    .storageType(target.getStorageType())   // ← BUG: copies DIFF if target was DIFF
    .build());
```

When `target.getStorageType() == DIFF`, the rollback entity is created with `storageType = DIFF`. Downstream:

- `getDownloadUrl()` checks `version.getStorageType() == StorageType.DIFF` → returns HTTP 202 with **empty** `downloadUrl`. The frontend V1 preview handler sees an empty URL and exits without loading anything.
- `getDiff()` calls `presignOrReconstruct()` which dispatches a spurious `RECONSTRUCT_DOCUMENT` task to Redis for the rollback version; the worker will try and fail to find a staging diff for it.
- The Download button sends `getVersionDownloadUrl` → 202 → no file delivered to the user.

**Fix — `vcs-backend/.../version/service/VersionService.java`, `rollbackVersion()`:**

Always force `storageType = SNAPSHOT` on the rollback entity. The content at the permanent key is a full document (placed there by the S3 copy), regardless of what the target version was:

```java
VersionEntity rollback = versionRepository.save(VersionEntity.builder()
    .docId(docId).versionNumber(nextNumber).status(VersionStatus.DRAFT).isDraft(true)
    .isUploading(false).checksum(target.getChecksum())
    .storageType(StorageType.SNAPSHOT)   // ← always SNAPSHOT: we copied the full document
    .build());
```

### Root cause B — S3 copy fails with `NoSuchKeyException` when rolling back to an unreconstructed DIFF version

The permanent S3 key for a DIFF version (`documents/{docId}/v{N}`) is only written by the worker's `RECONSTRUCT_DOCUMENT` task, which is triggered when a user downloads that version. If no one has ever downloaded a DIFF version, its permanent key does not exist. The `s3Client.copyObject(...)` call in `rollbackVersion()` references this non-existent key as its source and throws `software.amazon.awssdk.services.s3.model.NoSuchKeyException` (wrapped in an `SdkClientException` or `S3Exception`), propagating as HTTP 500.

**Fix — `vcs-backend/.../version/service/VersionService.java`, `rollbackVersion()`:**

Add a guard before the S3 copy: if the target is a DIFF version, verify the permanent key exists; if not, trigger `RECONSTRUCT_DOCUMENT` and return HTTP 409 instructing the caller to wait for reconstruction before retrying:

```java
@PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
public VersionEntity rollbackVersion(UUID docId, UUID targetVersionId, UUID callerId) {
    VersionEntity target = resolveAndValidate(docId, targetVersionId);
    requireRole(documentFacade.resolveOrgId(docId), callerId, "AUTHOR", "ADMIN");

    // For DIFF versions, the permanent key only exists after reconstruction.
    // Check whether it is already there; if not, dispatch RECONSTRUCT and reject.
    if (target.getStorageType() == StorageType.DIFF) {
        String permanentKey = S3KeyTemplates.permanentVersion(docId, target.getVersionNumber());
        boolean exists = s3KeyExists(permanentKey);
        if (!exists) {
            // Kick off reconstruction so the user can retry after it completes.
            diffTaskPublisher.publish(ReconstructTaskMessage.builder()
                .taskType(WorkerTaskType.RECONSTRUCT_DOCUMENT)
                .docId(docId)
                .versionId(targetVersionId)
                .recipientId(callerId)
                .expectedChecksum(target.getChecksum())
                .targetVersionNumber(target.getVersionNumber())
                .build());
            throw new AppException(HttpStatus.CONFLICT,
                "Target version has not been reconstructed yet. " +
                "Reconstruction has been queued — please retry in a moment.");
        }
    }

    int nextNumber = versionRepository.findTopByDocIdOrderByVersionNumberDesc(docId)
        .map(v -> v.getVersionNumber() + 1).orElse(1);

    s3Client.copyObject(CopyObjectRequest.builder()
        .sourceBucket(s3Properties.bucket())
        .sourceKey(S3KeyTemplates.permanentVersion(docId, target.getVersionNumber()))
        .destinationBucket(s3Properties.bucket())
        .destinationKey(S3KeyTemplates.permanentVersion(docId, nextNumber))
        .build());

    VersionEntity rollback = versionRepository.save(VersionEntity.builder()
        .docId(docId).versionNumber(nextNumber).status(VersionStatus.DRAFT).isDraft(true)
        .isUploading(false).checksum(target.getChecksum())
        .storageType(StorageType.SNAPSHOT)   // ← always SNAPSHOT
        .build());
    documentFacade.updateLatestVersionId(docId, rollback.getId());
    return rollback;
}

/** Returns true if the S3 object at the given key exists in the configured bucket. */
private boolean s3KeyExists(String key) {
    try {
        s3Client.headObject(b -> b.bucket(s3Properties.bucket()).key(key));
        return true;
    } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
        return false;
    }
}
```

**Fix — `vcs-frontend/src/views/DocumentViewerView.jsx`, rollback `onError`:**

Surface the 409 "retry" message to the user so they know to wait:

```jsx
onPress={() => rollback.mutate(
    { docId, versionId: selectedVersion.id },
    {
        onError: (err) => {
            const msg = err?.response?.data?.message ?? err?.message;
            addToast({ title: 'Rollback not ready', description: msg, color: 'warning' });
        }
    }
)}
```

---

## Non-interference checklist

| Issue | Files touched | Conflicts with |
|-------|---------------|---------------|
| 1A | `DocumentMapper.java` | None |
| 1B/1C | `DocumentMapper.java`, `DocumentViewerView.jsx` | 1A (apply together — same method in mapper) |
| 2 | `VersionService.java` | None |
| 3 | `VersionService.java`, `RequestService.java` | 2 (different methods in `VersionService`; no conflict) |
| 4A | `VersionService.java` (`rollbackVersion`) | None |
| 4B | `VersionService.java` (`rollbackVersion`), `DocumentViewerView.jsx` | 4A (same method; apply together as one combined change) |

**Issues 1A + 1B must be applied together** — both modify `DocumentMapper.applyUpdate()`.  
**Issues 4A + 4B must be applied together** — both modify `rollbackVersion()`.  
All other issues are fully independent.

