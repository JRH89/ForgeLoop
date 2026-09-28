export type IssueReferenceResult = { issueNumber: number } | { error: string };

/** Resolve a pasted issue number or canonical GitHub issue URL against the selected repository. */
export function parseIssueReference(value: string, repository: string): IssueReferenceResult {
  const input = value.trim();
  if (/^\d+$/.test(input)) return issueNumberResult(input);

  let url: URL;
  try {
    url = new URL(input);
  } catch {
    return { error: 'Enter a positive issue number or a GitHub issue URL.' };
  }
  if (url.protocol !== 'https:' || url.hostname !== 'github.com' || url.username || url.password || url.port) {
    return { error: 'Use an https://github.com issue URL.' };
  }

  const match = /^\/([^/]+\/[^/]+)\/issues\/(\d+)\/?$/.exec(url.pathname);
  if (!match) return { error: 'That is not a GitHub issue URL. Pull request links are not supported.' };
  if (match[1].toLowerCase() !== repository.toLowerCase()) {
    return { error: `Choose an issue from ${repository}.` };
  }
  return issueNumberResult(match[2]);
}

function issueNumberResult(value: string): IssueReferenceResult {
  const issueNumber = Number(value);
  return Number.isSafeInteger(issueNumber) && issueNumber > 0 && issueNumber <= 2_147_483_647
    ? { issueNumber }
    : { error: 'Issue numbers must be between 1 and 2,147,483,647.' };
}
