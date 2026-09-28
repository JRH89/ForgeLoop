package io.forgeloop.runner;

import java.util.List;

/** Text-only chat context sent to an enrolled runner; no repository checkout or GitHub token is granted. */
public record IssueChatTurnGrant(String id, String repository, String draftTitle, String draftBody,
                                 List<String> acceptanceCriteria, List<Message> messages) {
    public record Message(String role, String content) { }
}
