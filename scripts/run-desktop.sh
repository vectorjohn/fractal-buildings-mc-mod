#!/usr/bin/env bash
# Run the dev client on a real desktop with GPU rendering, for manual testing.
#
#   ./scripts/run-desktop.sh [extra gradle args]
#
# From a terminal on the desktop, it uses that session's display. Over SSH (no
# WAYLAND_DISPLAY/DISPLAY set), it targets your own logged-in Wayland session, if any.
#
# The dev shell pins Vulkan to Mesa's software driver (lavapipe) for headless/xvfb runs.
# This script points Vulkan at the host's hardware drivers instead. Minecraft's OpenGL
# backend fails on this setup (EGL_BAD_DISPLAY) and the game falls back to Vulkan.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -z "${WAYLAND_DISPLAY:-}" && -z "${DISPLAY:-}" ]]; then
	export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
	if [[ ! -S "$XDG_RUNTIME_DIR/wayland-0" ]]; then
		echo "No display found: log into a desktop session as $(id -un) first." >&2
		exit 1
	fi
	export WAYLAND_DISPLAY=wayland-0
	export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=$XDG_RUNTIME_DIR/bus}"
fi

# Hardware Vulkan drivers: NixOS keeps them under /run/opengl-driver. Elsewhere, unset
# VK_DRIVER_FILES so the loader searches the system's default locations.
icds=()
for f in /run/opengl-driver/share/vulkan/icd.d/{nvidia_icd,intel_icd.*,radeon_icd.*}.json; do
	[[ -e "$f" ]] && icds+=("$f")
done
if ((${#icds[@]})); then
	vk_env=(VK_DRIVER_FILES="$(IFS=:; echo "${icds[*]}")")
else
	vk_env=(-u VK_DRIVER_FILES)
fi

# SDL_VIDEO_X11_XINPUT2=0 is a VNC workaround; real desktops want XInput2.
exec scripts/dev --fresh env -u SDL_VIDEO_X11_XINPUT2 "${vk_env[@]}" ./gradlew runClient "$@"
