# Bussiness model

**SAAS**

# Frontend pages
## 1. Landing page
 - Login (redirects to Keycloak via `GET /auth/login`)
 - Register
 - User guide / About

## Navbar
- Main (organizations) page button
- My documents page (documents page) **If the user is an author / reviewer**
- Reviews page **If the user is a reviewer** (pending versions awaiting review)
- Notifications page
- Admin panel **If the user is an admin**
- Profile / user dropdown

## 2. Main page (`/organizations`)
- Create/edit organization → pop-up form
- Choose organization → documents page

## 3. Documents page (`/organizations/:orgId/documents`)
- Search bar for documents.
- Filters (by category, by author, by status)
- Document view (cards)
	- Name
	- Author
	- Status (**Only for author / reviewer / admin**)
- PAGINATION

## 4. Document viewer (`/organizations/:orgId/documents/:docId`)
- Document name
- Version selector (stylized slider)
- Download button
- New version button (upload SNAPSHOT or DIFF)
- Rollback to this version (if this is an old version) button
- Diff visualizer (split or unified mode, rendered in-browser via WASM diff library)
- Comments panel
- Category selector (for admins/authors)
- Request review button (author → sets status to PENDING_REVIEW)
- Approve / Reject buttons (for reviewers)

## 5. Reviewer queue (`/reviews`)
- List of all versions pending review across all organisations the user is a reviewer in
- Approve / Reject per item
- Link to full document viewer

## 6. Admin panel (`/organizations/:orgId/admin`)
- Add users to organisation
- Manage user roles
- Organisation config

## 7. Notifications page (`/notifications`)
- List of all notifications (version approved/rejected, fork request updates, etc.)
- Mark as read

## Routes summary
| Path | View |
|---|---|
| `/` | LandingView |
| `/organizations` | OrganizationsView |
| `/organizations/:orgId/documents` | DocumentsView |
| `/organizations/:orgId/documents/:docId` | DocumentViewerView |
| `/organizations/:orgId/admin` | AdminPanelView |
| `/documents/my` | DocumentsView (my docs filter) |
| `/documents/:docId` | DocumentViewerView |
| `/reviews` | ReviewerView |
| `/notifications` | NotificationsView |


# Functionality

- **Documents** 
  - Properties 
    - author 
    - name  *(field is `name`, not `title`)*
    - list of reviewer IDs  *(stored in `document_reviewers` join table)*
    - latest version 
    - latest approved version
    - status: `DRAFT` | `PENDING_REVIEW` | `APPROVED` | `REJECTED`
    - category (optional)

  - versions
    - version number (sequential per document)
    - storage type: `SNAPSHOT` (full content) or `DIFF` (delta vs previous version)
    - status: `DRAFT` | `PENDING` | `APPROVED` | `REJECTED`
    - `isUploading` flag — cleared by MinIO webhook / worker when upload is confirmed
    - `checksum` (SHA-256 of content, set by worker for DIFF versions)
    - `diffPreview` — short unified-diff excerpt for DIFF versions
    - comments

- **Users**
  - all
    - can view *approved* version of documents + can view difference
  - admin - all rights
    - can add a list of reviewers to documents
  - author
    - create / update / delete
      - *documents* - request delete
      - *versions* - rollback (adds new version - copy of the desired old one), *immutable*
      - *drafts* - version that is visible just to the author
    - can only edit his own documents
    - can request to become *coauthor* on a doc
    - can approve coauthor requests
    - can view *any* version of documents + can view difference
  - reviewer
    - can only manage documents he has access to
    - reviews and approves/rejects new versions (*approval changes document status from PENDING_REVIEW to APPROVED/REJECTED*)
    - can add comments on versions
    - can view *any* version of documents + can view difference
  - reader
    - reads documents
    - report problem ?

> **Multi-role:** A user may hold more than one role within the same organisation (V10 DB migration).

- **Organizations**
  - name
  - list of users, user rights

# DB

### Postgres Tables (schema `vcs_core`):

- **documents** — `id`, `org_id`, `author_id`, `name`, `status`, `category_id`, `latest_version_id`, `latest_approved_version_id`
- **document_reviewers** — `document_id`, `reviewer_id` (join table for reviewer list)
- **user_profiles** — `id` (= Keycloak `sub`), `name`, `email`, `photo_url`
- **organizations** — `id`, `name`, `created_at`, `created_by`
- **org_memberships** — `id`, `org_id`, `user_id`, `role`  *(multi-role per user since V10)*
- **categories** — `id`, `org_id`, `name`
- **versions** — `id`, `doc_id`, `version_number`, `status`, `is_draft`, `is_uploading`, `storage_type`, `checksum`, `diff_preview`, `created_at`, `created_by`
- **comments** — `id`, `version_id`, `author_id`, `body`, `created_at`
- **fork_requests** — `id`, `requester_id`, `doc_id`, `version_id`, `status` (`PENDING`/`APPROVED`/`REJECTED`/`CANCELLED`)
- **notifications** — `id`, `recipient_id`, `type`, `payload` (jsonb), `read_at`, `created_at`
- **spring_modulith_event_publication** — Spring Modulith event store (V3 migration)

### Document / file storage:

Document content is stored in **S3 / MinIO** (bucket `vcs-documents`), **not** in PostgreSQL.
S3 key patterns:
- `documents/{docId}/v{n}` — permanent full snapshot
- `tmp/{docId}/v{n}.diff` — staging diff awaiting worker verification
- `tmp/{docId}/v{n}` — temporary reconstructed file for download

### Backend services:

- **vcs-backend** — Spring Boot 4 REST API (port 8080)
- **vcs-backend-worker** — GraalVM native-image service; reads VERIFY_DIFF / RECONSTRUCT_DOCUMENT tasks from Redis Stream `vcs.diff.jobs`; applies diffs, verifies checksums, writes results to DB
- **Redis** — Stream queue between backend and worker (port 16379 host / 6379 container)
- **PostgreSQL 17** — primary database (port 55432 host / 5432 container, schema `vcs_core`)
- **MinIO** — S3-compatible object storage (API port 19000, Console port 19001)
- **Keycloak 26.2** — identity provider, JWT issuer (port 18080)
