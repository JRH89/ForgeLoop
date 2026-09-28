import { describe, expect, it } from 'vitest';
import { parseIssueReference } from './issueReference';

describe('parseIssueReference', () => {
  it('accepts positive issue numbers and canonical issue URLs for the selected repository', () => {
    expect(parseIssueReference(' 42 ', 'acme/project')).toEqual({ issueNumber: 42 });
    expect(parseIssueReference('https://github.com/acme/project/issues/42?tab=activity#issuecomment-7', 'acme/project'))
      .toEqual({ issueNumber: 42 });
    expect(parseIssueReference('https://github.com/ACME/Project/issues/0042/', 'acme/project'))
      .toEqual({ issueNumber: 42 });
  });

  it.each(['0', '-1', '2147483648', 'issue 42', 'https://github.com/acme/project/pull/42',
    'https://github.com.evil.example/acme/project/issues/42', 'http://github.com/acme/project/issues/42',
    'https://other.example/redirect?url=https://github.com/acme/project/issues/42',
    'https://github.com/other/project/issues/42', 'https://user@github.com/acme/project/issues/42'])('rejects malformed, unsafe, or wrong-repository reference %s', value => {
    expect(parseIssueReference(value, 'acme/project')).toHaveProperty('error');
  });
});
