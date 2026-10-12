# Conversation deletion

Open a conversation and choose Delete conversation, then confirm. Owners can
delete their own chats; administrators can delete other chats in the same
workspace. Viewers cannot delete. A locked transaction checks workspace and
ownership and deletes messages before the conversation. Pending work can be
deleted before a runner claims it; running replies must finish first.

Deletion is permanent. GitHub issues, feature runs, provider accounting and audit
records remain. No provider call is made by deletion.

Chat replies run on an enrolled customer runner, not the website. A local dev
stack needs a separately paired runner pointing to http://localhost:5173; a
runner paired to the production hostname cannot claim local database jobs.
Do not repoint a working production runner just to inspect UI changes.
