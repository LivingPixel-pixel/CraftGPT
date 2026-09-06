# Phase 7 — Recovery operations and world linkage

Phase 7 turns the Phase-6 journal history into an operational recovery system while preserving the local-first trust boundary.

## Recovery storage

- Live journals remain under the world folder at `craftgpt/placements/<player UUID>/`.
- Recovery archives are written under `craftgpt/recovery-archives/<player UUID>/`.
- Each archive contains the immutable placement snapshot and its state, a snapshot hash and an archive-wide SHA-256 integrity hash.
- Archive writes are staged and atomically replaced where supported.
- Import validates schema, ownership, UUIDs, timestamps, block states, NBT limits and both hashes.
- Import never overwrites a live journal. An interrupted archived job is restored as `interrupted`, never as an active mutation.

Snapshot blocks and optional block-entity NBT never leave the server. The history packet contains coordinate-free metadata; an explicit visualization request returns only the affected bounding box.

## Retention

The client setting offers 10, 25 or 50 live journals. Cleanup is explicit and double-confirmed. The server processes entries newest-first, skips busy jobs, exports every entry successfully, and only then removes its live journal. The current head is never removed.

## UX

`/craftgpt history` shows whether an entry is live (`L`), archived (`A`) or both (`L+A`). Undo and redo remain available only for live journals. **Recovery tools** provides:

- show/hide an in-world client-only particle outline;
- export or refresh a server-local archive;
- restore an archive-only entry into the live journal history;
- return to the current preview when its build ID matches;
- archive and prune older live journals to the configured limit.

## Automated verification

The automated suite covers archive round trips, integrity rejection after tampering, non-overwriting restore, journal removal guards, bounded maintenance requests, inspection bounds and strict history metadata. It does not mutate or start a real Minecraft world.

## Manual live-world release checklist

1. Place a normal build, reopen history and verify `L`, marker bounds, undo and redo.
2. Export it and verify `L+A`; restart the world and recheck the entry.
3. Create more journals than the configured limit, confirm cleanup and verify older entries become `A`.
4. Import an archive-only entry, verify `L+A`, then perform conflict-aware undo/redo.
5. With block-entity replacement enabled, replace a filled chest, undo and verify inventory/NBT restoration.
6. Stop the game during a larger placement, restart and verify `interrupted` recovery.
7. Inspect an entry from another dimension; verify no particles appear until entering that dimension.
