# shellcheck shell=bash disable=SC2034 # the WK_* maps are read by the scripts that source this
# The worker pool: machines that run jobs for this project over ssh (scripts/remote.sh, scripts/anywhere.sh,
# scripts/quiet-check.sh --worker). Sourced, not run.
#
# The pool lives in a LOCAL, gitignored file, so no host name is ever committed:
#   <main repo root>/.claude/workers.conf     (FB_WORKERS_CONF overrides the path)
# Start from the committed example: cp scripts/workers.conf.example .claude/workers.conf (in the main checkout).
# No file means no workers: everything runs on this host, and a command that needs a worker says so.
#
# Format: INI sections, one per worker; the section name is the worker's symbolic name (used in --worker,
# --only, --prefer and in logs). Keys (all but host optional):
#   host     = ssh host (an ~/.ssh/config alias or user@host); must accept BatchMode (key) logins
#   roles    = space-separated roles: perf (the benchmark machine for quiet measurements; perf.sh / profile.sh
#              timings go only there), heavy (client tests, galleries), suite (full fast suites); default "heavy suite"
#   slots    = jobs the worker's queue runs at once (default 1); scripts/anywhere.sh fills up to this many
#   heavy_slots = at most this many of them heavy at once (client runs, full test-all, the order test, flythrough;
#              anywhere.sh's kinds heavy and full plus order-test.sh); default = slots. Light and suite jobs fill
#              the remaining slots. Only jobs sent through scripts/anywhere.sh are counted
#   enabled  = yes|no (default yes); a disabled worker is listed but never used
#   memory   = per-job memory cap, free text (e.g. "MemoryMax 28 %, about 7.9 GiB")
#   renderer = GPU / display notes, free text
#   vk_icd   = Vulkan ICD json on the worker for GPU jobs (default: the radeon one under /run/opengl-driver)
#   specs    = free text (CPUs, RAM, disk)
#   note     = free text
# Lines starting with # or ; are comments. Unknown keys are kept and ignored.
#
#   workers_load            parse the file (once); sets WK_NAMES (all, in file order), WK_CONF, WK_MISSING=0|1
#                           and the maps WK_HOST WK_ROLES WK_SLOTS WK_HEAVY WK_ENABLED WK_MEMORY WK_RENDERER WK_ICD
#                           WK_SPECS WK_NOTE; returns 2 (with a message) on a malformed file
#   workers_enabled         enabled worker names, one per line
#   workers_resolve SPEC    enabled names matching SPEC, one per line: a worker name, a role, or an ssh host
#                           (the host form is the deprecated alias kept for old command lines); "any" = all
#   worker_default          the first enabled perf worker, else the first enabled worker (nothing: none)
#   worker_key NAME         a file-name-safe key for NAME's host (claims, caches, queue tickets)
#   worker_has_role NAME R
#   workers_hint            one line: where the file goes and the example to copy

WK_LOADED=0

workers_conf_path() {
	local here common
	if [[ -n ${FB_WORKERS_CONF:-} ]]; then printf '%s\n' "$FB_WORKERS_CONF"; return; fi
	here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	if common=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then
		printf '%s/.claude/workers.conf\n' "$(dirname "$common")"
	else
		printf '%s/.claude/workers.conf\n' "$here"
	fi
}

workers_hint() {
	local here
	here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	echo "worker pool: configure it in $(workers_conf_path) (gitignored; start from $here/scripts/workers.conf.example)"
}

workers_load() {
	(( WK_LOADED )) && return 0
	declare -gA WK_HOST=() WK_ROLES=() WK_SLOTS=() WK_HEAVY=() WK_ENABLED=() WK_MEMORY=() WK_RENDERER=() WK_ICD=() WK_SPECS=() WK_NOTE=()
	WK_NAMES=() WK_MISSING=0
	WK_CONF=$(workers_conf_path)
	WK_LOADED=1
	if [[ ! -f $WK_CONF ]]; then WK_MISSING=1; return 0; fi
	local line sec="" k v n=0 err=""
	while IFS= read -r line || [[ -n $line ]]; do
		n=$((n + 1))
		line=${line%$'\r'}
		[[ $line =~ ^[[:space:]]*([#\;].*)?$ ]] && continue
		if [[ $line =~ ^[[:space:]]*\[([A-Za-z0-9_-]+)\][[:space:]]*$ ]]; then
			sec=${BASH_REMATCH[1]}
			[[ $sec == local || $sec == any ]] && { err="line $n: '$sec' is reserved"; break; }
			[[ -n ${WK_SLOTS[$sec]+x} ]] && { err="line $n: worker '$sec' twice"; break; }
			WK_NAMES+=("$sec"); WK_ROLES[$sec]="heavy suite"; WK_SLOTS[$sec]=1; WK_ENABLED[$sec]=yes
			continue
		fi
		if [[ $line =~ ^[[:space:]]*([A-Za-z_]+)[[:space:]]*=[[:space:]]*(.*)$ ]]; then
			[[ -n $sec ]] || { err="line $n: key outside a [worker] section"; break; }
			k=${BASH_REMATCH[1]} v=${BASH_REMATCH[2]}
			v=${v%"${v##*[![:space:]]}"}
			case $k in
				host) WK_HOST[$sec]=$v ;;
				roles) WK_ROLES[$sec]=$v ;;
				slots) [[ $v =~ ^[0-9]+$ && $v -ge 1 ]] || { err="line $n: slots takes a whole number >= 1"; break; }; WK_SLOTS[$sec]=$v ;;
				heavy_slots) [[ $v =~ ^[0-9]+$ ]] || { err="line $n: heavy_slots takes a whole number >= 0"; break; }; WK_HEAVY[$sec]=$v ;;
				enabled) case ${v,,} in yes|true|1|on) WK_ENABLED[$sec]=yes ;; no|false|0|off) WK_ENABLED[$sec]=no ;;
					*) err="line $n: enabled takes yes or no"; break ;; esac ;;
				memory) WK_MEMORY[$sec]=$v ;;
				renderer) WK_RENDERER[$sec]=$v ;;
				vk_icd) WK_ICD[$sec]=$v ;;
				specs) WK_SPECS[$sec]=$v ;;
				note) WK_NOTE[$sec]=$v ;;
			esac
			continue
		fi
		err="line $n: not a [section] or key = value: $line"; break
	done < "$WK_CONF"
	if [[ -z $err ]]; then
		for sec in "${WK_NAMES[@]}"; do
			[[ -n ${WK_HOST[$sec]:-} ]] || { err="worker '$sec' has no host"; break; }
			[[ -n ${WK_HEAVY[$sec]:-} ]] || WK_HEAVY[$sec]=${WK_SLOTS[$sec]}
			(( WK_HEAVY[$sec] <= WK_SLOTS[$sec] )) || { err="worker '$sec': heavy_slots ${WK_HEAVY[$sec]} is more than slots ${WK_SLOTS[$sec]}"; break; }
		done
	fi
	if [[ -n $err ]]; then
		echo "workers: $WK_CONF: $err" >&2
		WK_NAMES=()
		return 2
	fi
	return 0
}

workers_enabled() {
	local w
	workers_load || return 2
	for w in "${WK_NAMES[@]}"; do [[ ${WK_ENABLED[$w]} == yes ]] && echo "$w"; done
	return 0
}

worker_has_role() { [[ " ${WK_ROLES[$1]:-} " == *" $2 "* ]]; }

workers_resolve() {   # SPEC: name | role | host | any
	local s=$1 w found=0
	workers_load || return 2
	for w in "${WK_NAMES[@]}"; do
		[[ ${WK_ENABLED[$w]} == yes ]] || continue
		if [[ $s == any || $w == "$s" || ${WK_HOST[$w]} == "$s" || $(worker_key "$w") == "$s" ]] || worker_has_role "$w" "$s"; then
			echo "$w"; found=1
		fi
	done
	(( found ))
}

worker_default() {
	local w
	workers_load || return 2
	for w in "${WK_NAMES[@]}"; do [[ ${WK_ENABLED[$w]} == yes ]] && worker_has_role "$w" perf && { echo "$w"; return 0; }; done
	for w in "${WK_NAMES[@]}"; do [[ ${WK_ENABLED[$w]} == yes ]] && { echo "$w"; return 0; }; done
	return 1
}

host_key() { local h=${1#*@}; printf '%s\n' "${h//[^A-Za-z0-9_-]/-}"; }
worker_key() { host_key "${WK_HOST[$1]:-$1}"; }
