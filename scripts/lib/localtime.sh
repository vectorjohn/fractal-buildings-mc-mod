# shellcheck shell=bash
# fb_localtime: a stdin -> stdout filter for pages people read. Every ISO-8601 UTC stamp the scripts store
# (2026-11-06T06:40:22Z, 2026-11-06T06:40Z, fractional seconds dropped) becomes the host's local time with
# its zone, at the same precision: "2026-11-05 22:40:22 PST", "2026-11-05 22:40 PST". Stored state (tsv,
# .state, logs) stays UTC; convert only when displaying. The zone is the process's: TZ if set, else
# /etc/localtime (also under systemd --user, which sets no TZ), UTC when neither resolves.
# Compact stamps (20261106T020002Z, in folder names) and stamps glued to a digit or letter are left alone.
# Needs gawk (mktime with the UTC flag, strftime); without it the text passes through unchanged.
fb_localtime() {
	local aw
	aw=$(command -v gawk 2>/dev/null) || { cat; return; }
	"$aw" '
		{
			out = ""; rest = $0
			while (match(rest, /([0-9][0-9][0-9][0-9])-([0-9][0-9])-([0-9][0-9])T([0-9][0-9]):([0-9][0-9])(:([0-9][0-9])(\.[0-9]+)?)?Z/, m)) {
				pre = substr(rest, 1, RSTART - 1); post = substr(rest, RSTART + RLENGTH)
				if (pre ~ /[A-Za-z0-9]$/ || post ~ /^[A-Za-z0-9]/) {   # part of a longer token: keep it
					out = out substr(rest, 1, RSTART + RLENGTH - 1); rest = post; continue
				}
				t = mktime(m[1] " " m[2] " " m[3] " " m[4] " " m[5] " " (m[7] == "" ? 0 : m[7]), 1)
				rep = (t < 0) ? m[0] : strftime(m[7] == "" ? "%Y-%m-%d %H:%M %Z" : "%Y-%m-%d %H:%M:%S %Z", t)
				out = out pre rep; rest = post
			}
			print out rest
		}'
}
# fb_localtime_re: an ERE for the stamps fb_localtime writes (with or without seconds; the zone is an
# abbreviation such as PST, or a numeric offset such as +0530 where the zone has no abbreviation)
# shellcheck disable=SC2034
FB_LOCALTIME_RE='[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}(:[0-9]{2})? ([A-Z][A-Za-z]*|[+-][0-9]{2,4})'
