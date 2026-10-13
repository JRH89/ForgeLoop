-- Preserve existing explicit label gating; bypass is administrator opt-in.
ALTER TABLE repository_connection ADD COLUMN require_label BOOLEAN NOT NULL DEFAULT TRUE;
