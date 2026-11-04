# FBNOTESEED: /fbnote records the seed for operators on a server

- Route: vanilla-only, no networking. On a remote server `FbNoteClient` checks the command tree the server sent (it lists `/seed` only to players allowed to run it, so non-ops see nothing and no error), sends `/seed` through the connection, and a `ClientReceiveMessageEvents.ALLOW_GAME` listener swallows the one `commands.seed.success` reply (not shown in chat) and parses the number (`parseSeed`). The JSON is written when the reply arrives, or after 40 client ticks (~2 s) without the seed (field stays `null`). Nothing blocks the game thread.
- Rejected: a mod packet (no networking in the mod; needs the mod server-side); parsing arbitrary chat (only the translatable key is intercepted, only while waiting).
- Tests: `FbNoteCommandTest.parsesSeedReply` (unit). `FbNoteClientGameTest` gains a second note with `FbNoteClient.testForceRemote` (singleplayer treated as remote; player is an op of the test world) that must carry seed 987654321 and no planner data. Not tested: a real dedicated server, a non-op (no `/seed` node), the chat staying clean.
- Docs: README-player "Reporting a bug" (#TODO removed), PLAYTEST.md seed row.
