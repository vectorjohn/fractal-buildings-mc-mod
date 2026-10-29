# Pick the Vulkan driver for a headless Minecraft client (Xvfb). Sourced, inside the dev shell:
#
#   source scripts/vk-select.sh && fb_vk_select    # reads FB_VK; exports VK_DRIVER_FILES etc.
#
# FB_VK=intel (the default since John's go-ahead on 2026-10-26): Mesa's Intel Vulkan driver (ANV) on
# the Intel UHD 770 iGPU. Before switching, it checks that the Intel ICD file exists, that an Intel
# render node (/dev/dri/renderD*, driver i915 or xe) is there and accessible, and that `vulkaninfo`
# with only the Intel ICD lists an Intel GPU. If any check fails it logs why and falls back to
# lavapipe, so a machine without the iGPU still runs the tests. Only the Intel ICD is ever listed,
# so the loader cannot see the NVIDIA card (not approved for tests).
# FB_VK=lavapipe: Mesa's CPU renderer, which the dev shell's VK_DRIVER_FILES already points at.
# Anything else is refused (return 2).
#
# Xvfb has no DRI3, which Mesa needs to present a GPU image to an X window; MESA_VK_WSI_DEBUG=sw
# takes Mesa's software present path (the GPU renders, each frame is copied to the window by the
# CPU). See docs/research/spikes/gpu-intel.md.
#
# The worker box (scripts/remote.sh) sets FB_VK_DRIVER_FILES to its own GPU's ICD; the dev shell's
# shellHook then exports VK_DRIVER_FILES=$FB_VK_DRIVER_FILES and keeps lavapipe's in FB_VK_LVP. There
# FB_VK=intel (the default) means "the worker's GPU" (FB_VK_USED=worker), and FB_VK=lavapipe still
# picks lavapipe. MESA_VK_WSI_DEBUG=sw is only set when not on an existing display (FB_USE_DISPLAY).
#
# After the call: FB_VK_USED=intel|lavapipe|worker (exported), and one "vk-select: ..." line on stderr.

fb_vk_select() {
	local want="${FB_VK:-intel}" tag="${FB_VK_LOG_TAG:-vk-select}"
	local lvp="${FB_VK_LVP:-${VK_DRIVER_FILES:-}}"
	case $want in
		lavapipe | intel) ;;
		*) echo "$tag: FB_VK must be lavapipe or intel, got '$want' (the NVIDIA GPU is not approved for tests)" >&2; return 2 ;;
	esac
	if [[ -n ${FB_VK_DRIVER_FILES:-} ]]; then
		if [[ $want == intel ]]; then
			export VK_DRIVER_FILES="$FB_VK_DRIVER_FILES" FB_VK_USED=worker
			[[ ${FB_USE_DISPLAY:-} == 1 ]] || export MESA_VK_WSI_DEBUG="${MESA_VK_WSI_DEBUG:-sw}"
			echo "$tag: renderer worker GPU (FB_VK_DRIVER_FILES), VK_DRIVER_FILES=$VK_DRIVER_FILES" >&2
			return 0
		fi
		export VK_DRIVER_FILES="$lvp"
	fi
	if [[ $lvp != *lvp_icd.* ]]; then
		echo "$tag: VK_DRIVER_FILES is not the dev shell's lavapipe ICD ('$lvp'); run inside scripts/dev" >&2
		return 2
	fi
	export FB_VK_USED=lavapipe
	if [[ $want == lavapipe ]]; then
		echo "$tag: renderer lavapipe (FB_VK=lavapipe), VK_DRIVER_FILES=$lvp" >&2
		return 0
	fi

	local icd="${lvp/lvp_icd./intel_icd.}" why="" node="" d drv
	if [[ ! -f $icd ]]; then
		why="Intel ICD not found: $icd"
	else
		for d in /sys/class/drm/renderD*; do
			[[ -e $d/device/driver ]] || continue
			drv=$(basename "$(readlink -f "$d/device/driver")")
			if [[ $drv == i915 || $drv == xe ]] && [[ -r /dev/dri/${d##*/} && -w /dev/dri/${d##*/} ]]; then
				node=/dev/dri/${d##*/}; break
			fi
		done
		if [[ -z $node ]]; then
			why="no accessible Intel render node (i915/xe) under /dev/dri"
		elif ! command -v vulkaninfo > /dev/null; then
			why="vulkaninfo not on PATH (flake: vulkan-tools)"
		else
			local summary
			summary=$(VK_DRIVER_FILES="$icd" MESA_VK_WSI_DEBUG=sw timeout 30 vulkaninfo --summary 2>&1) \
				|| why="vulkaninfo with the Intel ICD failed (exit $?): $(grep -m1 -iE 'error|fail' <<< "$summary")"
			if [[ -z $why ]] && ! grep -qE 'deviceName *= *Intel' <<< "$summary"; then
				why="vulkaninfo with the Intel ICD lists no Intel device"
			fi
		fi
	fi
	if [[ -n $why ]]; then
		echo "$tag: FALLBACK to lavapipe: $why" >&2
		echo "$tag: renderer lavapipe (fallback from FB_VK=intel), VK_DRIVER_FILES=$lvp" >&2
		return 0
	fi
	export VK_DRIVER_FILES="$icd" MESA_VK_WSI_DEBUG="${MESA_VK_WSI_DEBUG:-sw}" FB_VK_USED=intel
	echo "$tag: renderer intel ($node), VK_DRIVER_FILES=$VK_DRIVER_FILES MESA_VK_WSI_DEBUG=$MESA_VK_WSI_DEBUG" >&2
}
