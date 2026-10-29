# The dev shell moved to nix/flake.nix (2026-10-28). Run commands with
#   scripts/dev <cmd>                       (cached; the usual form)
#   nix develop path:./nix -c <cmd>         (bare fallback; keep the `path:`)
# This stub only fails with that message: a flake at the repo root makes nix copy the
# whole tracked repo (~115 MB) into the store for every commit and worktree.
{
  outputs = _: throw ''
    fractal-buildings: the flake moved to nix/. Use `scripts/dev <cmd>`, or the bare
    fallback `nix develop path:./nix -c <cmd>` (exactly `path:`; `./nix` copies the whole repo).'';
}
