# Bussiness model

**SAAS**

# Frontend functionality ==Unfinished==

## 1. Landing page

- Login
- Register
- User guide / About

## 2. Home page

1.  Create organization - become admin
2.  

# Functionality

- **Documents** 
  - Properties 
    - author 
    - title 
    - list of reviewers 
    - latest version 
    - latest approved version

  - versions
    - snapshots
    - approved status
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
    - reviews and approves/rejects new versions (*approval - whether the document is visible to others*)
    - can add comments on versions
    - can view *any* version of documents + can view difference
  - reader
    - reads documents
    - report problem ?

- **Organizations**
  - name
  - list of users, user rights

# DB ==Unfinished==

### Postgres Tables:

- **Docs** - metadata(id,organization,author,latest aproved verson, latest version...)
- **Users** - email,username,password,organizations+role in diff table
- **Snapshots** - document id, document blob
