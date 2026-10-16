# client-test-env

Build-file fix, not a ROADMAP item. `tasks.named('runClientGameTest')` in `mod/build.gradle` now calls `environment.remove('WAYLAND_DISPLAY')` and `environment 'SDL_VIDEODRIVER', 'x11'`. `runClient` is unchanged (`scripts/run-desktop.sh` needs Wayland).

Verified with the bare command and `WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1500`:
- Before the fix: the log said "SDL video driver: wayland" and the build failed.
- After: "Created window using SDL video driver: x11", BUILD SUCCESSFUL in 20s, and the hello-hut screenshot shows the hut as expected.

CLAUDE.md client-test line and NOTES updated. The scripts' own `env -u` workaround is now redundant but harmless.
