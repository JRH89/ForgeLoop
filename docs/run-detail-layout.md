# Run detail layout

GitHub delivery metadata (branch, commit, PR and merge state) sits below the run
summary metrics, before budget controls and task history.

Growing task, gate, criteria, event, escalation, review, evidence and audit panels
scroll independently at a maximum height of 28rem (24rem on mobile). Short panels
retain their natural height. Headings stay visible, and thin themed scrollbars
apply to these panels and evidence logs. Regions have accessible names and can be
focused to scroll with the keyboard; no records are truncated or removed.

The populated Playwright layout regression covers 320, 390, 430, 768 and 1440px
viewports, long histories, keyboard scrolling, thin scrollbars, branch placement
and horizontal overflow.
