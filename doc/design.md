# Bussiness model

**SAAS**

# Frontend pages
## 1. Landing page
 - Login
 - Register
 - User guide / About

## Navbar
- Main (organizations) page button
- Search page (documents page)
- My documents page (documents page) **If the user is an author / reviewer **
- Admin panel **If the user is an admin**
- Profile icon

## 2. Main page
- Create/edit organization -> pop-up form
- Choose organization -> documents page

## 3.Documents page
- Search bar for documents.
- filters (by date, by author)
- Document view (cards)
	- Title
	- Author
	- Status (**Only for author / reviewer / admin**)
- PAGINATION

## 4. Document viewer
- Document title
- Version selector (stylized slider)
- Download button
- New version button
- Rollback to this version (If this is an old version) button
- Change visualizer

## 5. Admin panel
- add users
- manage user roles
- organization config


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
