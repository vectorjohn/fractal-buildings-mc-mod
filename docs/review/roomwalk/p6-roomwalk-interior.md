# 6.18 ROOMWALK interior review (2026-10-27)

Mode: review. Round: first.
Counts: 0 violations, 1 should-fix, 4 suggestions, 0 proposed rules.

Looked at:
- Photo `docs/review/phase6/playtest-comments/2026-10-26_20.02.15.jpg` (the bug).
- Viewer cutaways (after only; the coder's gallery): `p6-roomwalk/viewer/out/gallery/house-medieval-cutaway.png`, `highrise-cut-typical-cutaway.png`. Both show 0 violations. Hooks are drawn as dots, so these pictures can't show bed orientation or lanes.
- Floor plans I generated myself. "Before" is 686cf4ea (the run-phase6 parent of the coder's last merge); "after" is dbb43bfb. I built the CLI in my own detached worktrees `.claude/worktrees/roomwalk-interior{,-before}`. Plans, all medieval unless named:
  - house 16x12, seeds 1, 3, 7
  - workshop 20x16, seeds 1, 3, 7
  - highrise 24x20, seeds 1, 3, 7 (every storey)
  - shop 12x16, seeds 2, 5
  - tower 14x14, seeds 2, 5 (keep)
  - bastion 12x12, seeds 0-7 (keep)
- Scripted counts over all those plans (node scripts in my scratchpad): bed head and wall, head under a window, beds across 3-wide rooms, furniture next to doors, rooms without a light hook, hook counts by kind.
- Not seen: any in-game interior after the fix (owed; no client run). No before/after cutaway pair.

## What the fix does (evidence)

- **Bed orientation is fixed everywhere.**
  - Before: 125 of 125 beds in the sample had the cell beyond the head as open floor (the head in mid-room, as in 20.02.15).
  - After: 0 of 125. Every head is against a wall or partition, and no beds were lost in houses, shops, towers, workshops or high-rises (125 -> 125).
- **Doors are clear.** 0 of 1246 non-ceiling hooks (bed heads included) touch a door, arch or entrance cell, orthogonally or diagonally.
- **LAMPS is folded in.** Standing lights went from 273 to 18; ceiling lights are unchanged (510). No room lost its last light hook: the unlit-room count is identical before and after. The rooms with no light hook (41 tower/wing "room" uses, 13 stair rooms) predate this item.
- **Optional pieces give way.** Tables went 307 -> 288 and benches 308 -> 304, the drop order DS-48 asks for. Chests went 209 -> 315: storage closets now hold two chests where one chest and a standing lamp stood. That's 2 of 9 cells, fine.
- **Case: highrise seed 3, storey 1, bedroom r7** (5x4).
  - Before: the head stood mid-room at (14,5) and a standing light sat under the ceiling light.
  - After: foot (12,5), head (12,6) against the south partition, chest (15,3), no standing lamp. Walkable, and reads as a bedroom.
- **Case: workshop seed 1, wing room r2** (4x3). The table was dropped, and the bed now runs foot (15,4) to head (15,5) against the south wall, with the door's inside cell (13,4) and the whole north row clear. That is the right trade in a 12-cell room.
- Invariant sweep: the coder reports 0 failures in 8000 cases per type. My own `check --seeds 0..49` over 7 types had not finished when I wrote this, so I do not rely on it.

## Violations

None. DS-13 ("never blocking a door, stair or path", accepted) now holds in every plan I read, and the new invariant enforces it. DS-48 itself is still `proposed`.

## Should fix

- **The wall-walk door strips a bed out of a furnished room.**
  - Where: bastion 12x12 keep, storey 1, room r4 (6x3, the room with the east door onto the walk at (11,3)), seeds 0 and 1 of 0-7 (2 of 8 seeds).
  - What happens: the hook layout puts the bed near the ring-line cell, then `WalkOps.endDoor` finds no clear cell and `dropHooksNear` deletes it. Hook id h6 is missing from the sequence; in seed 0 the table h10 in r5 goes too.
  - Result: an 18-cell room keeps a chest and a table but loses its only bed, although there is room for all three once the door cell is known.
  - Fix direction: the door is not furniture's to move. Reserve the walk door's inside cells (and the `=` step) before the interior layout runs, so the layout plans around it. If that ordering is costly, re-run the room's layout with the new door after the drop instead of leaving the gap. Keep the plain drop only for optional pieces. If a bed still cannot go anywhere, demote the room as the rest of DS-48 says.
  - (Answers the coder's question 2; see below.)

## Suggestions

1. **Bed heads under windows.**
   - Where: 25 of 125 beds have a window in the wall cell behind the head. All 3 workshop seeds (wing room, e.g. seed 1 r2 at (15,5) under the window at (15,6)), highrise seed 7 (16 of 35 beds), highrise seed 1 (2 of 2), house seed 7 r1 (head at (5,6), window at (6,6)), tower seed 5 r1 (storeys 0 and 7).
   - Why it matters: the window sits at sill +1, so nothing is physically blocked, but "head to a blank wall" is the idiom.
   - Change: when a bed has several valid placements, prefer one whose head wall cell is solid. Fall back to a window wall only when no blank wall works. This is cheap in the candidate order.
2. **Beds across 3-wide rooms.**
   - Where: 43 of 125 beds lie with the head on a long wall and the foot in the middle row of a 3-wide room, which cuts the room into two halves joined by a 1-wide gap. Examples: shop seed 2 storey 1 r1 (3x4, bed (2,2)-(3,2) leaves the north row as a dead pocket); tower seed 5 storeys 1-3 r5; highrise seed 3 (31 such beds, mostly in 6x3 flats).
   - Why it matters: it is walkable, and it beats the old count of 36 beds with heads mid-room. But DS-48's narrow-room clause (one long wall plus the far end wall) reads better.
   - Change: in a room whose short side is 3, try the bed along a long wall with its head on the end wall away from the door before trying it across the room.
3. **Lower the 2-wide threshold from short side ≥5 to ≥4.** See the answer to question 1. Keep passage-type "hall" rooms out.
4. **Guard the last bed.** No bedroom was demoted in my sample (beds 125 -> 125), but the demote step does not re-run DS-12. Before demoting, try every bed orientation with all optional pieces dropped. Never demote a dwelling's only bedroom without moving the bedroom use to another room. A test that every house and flat keeps at least one bed would pin this.

Outside this item, and already 7.5's to fix: high-rise flats and offices place benches and tables apart rather than as sets (e.g. highrise seed 3 storey 1 r6: table (5,3), benches (5,6) and (7,6) three rows away). This was the same before the change.

## Coder's questions

**(1) A 2-wide lane only when a wide-use room's short side is ≥5?** It is acceptable as a first cut, but I'd make it **≥4**.
- In a 4-wide shop, taproom or workshop, one row of pieces on each long wall still leaves exactly 2 clear in the middle. That is the arrangement we want, and a 2-wide check there rejects only the things that should be rejected: a piece standing proud of the wall, or a table in the middle.
- At 3 wide, keep 1 wide. The narrow-room clause already puts all furniture on one long wall, which leaves 2 in practice anyway.
- Exclude passage "hall" uses (house and workshop-wing halls, landings, e.g. workshop seed 1 r1 3x3) from `WIDE_USES`. The 2-wide rule is for great halls and banquet rooms, not corridors.
- The lane definition (cells of a clear 2x2 square plus one stub cell for door mouths) is right.
- This is not blocking. If ≥4 starts dropping required counters in narrow shops, keep 5 and leave a polish line.

**(2) A wall-walk door with no clear cell: drop the furniture or move the door?**
- Neither as the first resort. The door is fixed by the walk's geometry (the ring line at the walk level, one step up). Where it goes is the architect's floor layout, and the room's only way out. Moving it is the architect's call, not mine, and I wouldn't ask for it.
- Interior side: furniture yields to the door, but by being placed elsewhere, not deleted. Reserve the door cells before the layout, or re-lay the room after the cut. Drop only optional pieces. Demote the room only if the bed truly can't fit.
- Deleting the bed outright, which is what happens today in 2 of 8 bastion seeds, is the should-fix above.

## For John

Nothing blocking. The in-game look at a bedroom after the fix is still owed (client layer not run).

Verdict: should-fix round needed

# Re-review after r2 (2026-10-27, d660f5f4)

Mode: review. Round: re-review after the should-fix round.
Counts: 0 violations, 0 should-fix, 2 suggestions, 0 proposed rules.

Looked at: floor plans I generated at d660f5f4 (CLI built in my own detached worktree):
- bastion 12x12 keep, seeds 0-11, compared with the plans I made earlier at 686cf4ea (before) and dbb43bfb (r1);
- house 24x20 medieval, seeds 1, 3, 7, 25 (seed 3 has two `hall` rooms);
- hall 30x30 medieval, seed 1.

I also read `Walkability.WIDE_USES` and `MIN_SIDE_FOR_WIDE_LANE`, and grepped where the `hall` use is assigned. I did not look at any picture or anything in game in this round.

## Should-fix from round 1: fixed

- **No hook is deleted after the layout any more.** Hook ids run without gaps in all 12 bastion seeds. In r1, seed 0 lost h6 (a bed) and h10 (a table), and seed 1 lost h6 (a bed).
- **Seeds 0 and 1, storey 1.** Each room by a walk door keeps a bed, a chest and a table:
  - Seed 1 keeps its two rooms (24 and 18 cells), and its building is back to 5 beds.
  - In seed 0 the bed sits at (6,1), clear of the walk doors at (11,3..4) and (5..6,9) and of their steps.
- **Side effect (architect's side).** Reserving the door cells changes the floor layout. In 7 of 12 seeds (0, 3, 5, 6, 7, 8, 11), storey 1 is now one 48-cell room holding both walk doors where it used to be two rooms. Those bastions have one bed fewer (4 or 6 instead of 5 or 7). On the interior side that is fine: a guard room on the wall walk with a bed, a chest and a table is a period type, and the room is walkable. It is sparse, though (3 pieces in 48 cells, 8 %); see suggestion A. Whether the partition should survive is the architect's call.

## Q1 change: done, with one gap

- The wide lane now starts at short side 4, and `hall` has left `WIDE_USES`. For house halls that is right. In house 24x20 seed 3, r1 (6x4) and r2 (5x3) are furnished living halls (hearth, table), and a 1-wide lane is the house rule.
- **The gap:** I asked to exclude *passage* halls and keep great halls. But `hall` is also the use of:
  - the hall type's great hall (hall 30x30 seed 1, r5, 260 cells);
  - the shop's market hall (`ShopType`, `hall` at `MARKET_ROOM_AREA` and above).

  Both are now checked at 1 wide only. Nothing breaks today: their 8 pieces stand on the wall ring and the centre is open. But 7.5 will put long tables and benches in exactly these rooms. See suggestion B.

## Suggestions (polish backlog)

- **A.** The merged walk-storey room in bastions (storey 1, 48 cells, 7 of 12 seeds) wants a second furniture group (a second bed, or a bench and table group) so it doesn't read empty. It needs that only while it stays one room.
- **B.** Keep the 2-wide lane for great halls and market halls. Treat `hall` as wide when the room is large, for example short side 6 or more, or when the building type is `hall`, `shop` or `market`. House and wing halls stay at 1 wide. Do it before 7.5 furnishes halls.

Round-1 suggestions 1, 2 and 4 (bed heads under windows, beds across 3-wide rooms, guarding the last bed) stay in the polish backlog. The in-game look at a bedroom is still owed.

Verdict: meets the standard
