# FEATURE-142: Ticket assignment

## Acceptance criteria

1. Organization administrators can assign a ticket to a member of their organization.
2. A non-administrator cannot assign a ticket.
3. A ticket cannot be assigned to a user outside the ticket organization.
4. The GraphQL mutation returns the assigned ticket and an audit event is recorded.
5. The React interface renders eligible members and gives immediate optimistic feedback.
6. A rejected optimistic update is reverted and surfaced accessibly.
7. Backend, frontend, contract, and browser scenarios prove the above behavior.

## GraphQL mutation

```graphql
mutation Assign($ticketId: ID!, $assigneeId: ID!) {
  assignTicket(ticketId: $ticketId, assigneeId: $assigneeId) {
    id title status assignee { id displayName }
  }
}
```

The actor identity is supplied by `X-Actor-Id` in this demo. The API derives authorization from persisted organization membership; it never trusts a role or organization supplied by the mutation.

