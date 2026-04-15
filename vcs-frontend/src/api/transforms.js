const STATUS_MAP = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'In Review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

const STATUS_REVERSE_MAP = Object.fromEntries(
  Object.entries(STATUS_MAP).map(([k, v]) => [v, k])
);

export const displayStatus = (apiStatus) => STATUS_MAP[apiStatus] || apiStatus;

export const apiStatus = (displayStatus) => STATUS_REVERSE_MAP[displayStatus] || displayStatus;

export const formatVersionNumber = (num) => `v${num}`;

export const transformDocument = (doc, orgUsers = []) => {
  const author = orgUsers.find((u) => u.user_id === doc.author_id);
  return {
    ...doc,
    title: doc.name,
    status: displayStatus(doc.status),
    author: author?.name || doc.author_id,
    version: doc.latest_version_id ? undefined : 'v0',
  };
};

export const transformVersion = (version) => ({
  ...version,
  label: formatVersionNumber(version.version_number),
});
