#!/usr/bin/env bash
# shellcheck disable=SC2016,SC2012 # single quotes are for the fake worker's commands
# Self-test for scripts/remote.sh against a FAKE worker: fake ssh / rsync / job / journalctl on PATH,
# the "worker" is a temp dir, jobs run locally. No network, no Gradle; ~1 min.
#   scripts/remote-selftest.sh      prints one line per case, exits 1 if any fails
# Cases (WORKERHEALTH): a normal job with copy-back; a worker that doesn't answer is not sent to (255);
# --check; a stall mid-job then recovery (the job passes); a reboot that loses the job (JOB LOST 75);
# a failure after a suspend (75); a failure after a memory shrink (75); an OOM kill without signs of
# preemption (137); a passing job that saw a suspend (0 + WORKER SUSPENDED); --attach after the waiter
# died while the worker was away.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T=$(mktemp -d)
trap 'pkill -f "$T/" 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/root/scripts/lib" "$T/w/jobs" "$T/w/q" "$T/bin" "$T/wbin"
cp "$ROOT/scripts/remote.sh" "$T/root/scripts/"; cp "$ROOT/scripts/lib/workers.sh" "$T/root/scripts/lib/"
echo hello > "$T/root/README"
printf '[w]\nhost = fake-host\nroles = heavy suite\nslots = 2\npreemptible = yes\n' > "$T/workers.conf"
echo boot-1 > "$T/boot_id"; printf 'MemTotal:       16000000 kB\n' > "$T/meminfo"
export FB_WORKERS_CONF=$T/workers.conf FB_REMOTE_RETRY_MIN=1 FB_REMOTE_RETRY_MAX=2
unset FB_REMOTE_HOST

# ssh: options, host, command; "down" = no answer. /proc paths of the worker point at fake files.
cat > "$T/bin/ssh" <<EOF
#!/usr/bin/env bash
while [[ \$1 == -o ]]; do shift 2; done
shift   # host
[[ -e $T/down ]] && { sleep 0.2; exit 255; }
cd $T/w || exit 255
export HOME=$T/w PATH=$T/wbin:\$PATH
fix() { local s=\$1; s=\${s//\/proc\/sys\/kernel\/random\/boot_id/$T/boot_id}; printf '%s' "\${s//\/proc\/meminfo/$T/meminfo}"; }
cmd=\$(fix "\$*")
if [[ \$cmd == "bash -s"* ]]; then input=\$(cat); exec bash -c "\$cmd" <<< "\$(fix "\$input")"; fi
exec bash -c "\$cmd"
EOF
# rsync: src dst, "fake-host:" = the worker dir; -R with /./ keeps the path after it
cat > "$T/bin/rsync" <<EOF
#!/usr/bin/env bash
[[ -e $T/down ]] && exit 255
pos=()
while [[ \$# -gt 0 ]]; do case \$1 in -e) shift 2 ;; -*) shift ;; *) pos+=("\$1"); shift ;; esac; done
src=\${pos[0]/fake-host:/$T/w/} dst=\${pos[1]/fake-host:/$T/w/}
if [[ \$src == */./* ]]; then
	rel=\${src#*/./}; base=\${src%%/./*}
	[[ -e \$base/\$rel ]] || exit 0
	mkdir -p "\$dst/\$(dirname "\$rel")" && cp -a "\$base/\$rel" "\$dst/\$(dirname "\$rel")/"
elif [[ \$src == */ ]]; then mkdir -p "\$dst" && cp -a "\$src." "\$dst"
else cp -a "\$src" "\$dst"; fi
EOF
# job: a tiny queue in $T/w/q (n, ID.name, ID.st queued|running|finished, ID.ex, ID.out, ID.pid)
cat > "$T/wbin/job" <<EOF
#!/usr/bin/env bash
Q=$T/w/q
case \$1 in
	submit) name=\$2; shift 2; id=\$(( \$(cat \$Q/n 2>/dev/null || echo 0) + 1 )); echo \$id > \$Q/n
		echo "\$name" > \$Q/\$id.name; echo queued > \$Q/\$id.st
		setsid bash -c 'cd "\$1" || exit; echo running > "\$2.st"; "\${@:3}" > "\$2.out" 2>&1; echo \$? > "\$2.ex"; echo finished > "\$2.st"' _ "$T/w/jobs/\$name" "\$Q/\$id" "\$@" < /dev/null > /dev/null 2>&1 &
		echo \$! > \$Q/\$id.pid; echo \$id ;;
	status) if [[ -n \${2:-} ]]; then cat \$Q/\$2.st 2>/dev/null; exit 0; fi
		echo "ID   State      Output               E-Level  Times(r/u/s)   Command [run=0/2]"
		for f in \$Q/*.name; do [[ -e \$f ]] || continue; id=\$(basename "\$f" .name); st=\$(cat \$Q/\$id.st)
			if [[ \$st == finished ]]; then echo "\$id finished /x/out \$(cat \$Q/\$id.ex) 1.00/0.00/0.00 [\$(cat \$f)]bash .fb-remote-run.sh"
			else echo "\$id \$st /x/out [\$(cat \$f)]bash .fb-remote-run.sh"; fi; done ;;
	wait) while :; do [[ -e $T/down ]] && exit 255; [[ -e \$Q/\$2.st ]] || { echo "not found" >&2; exit 1; }
		[[ \$(cat \$Q/\$2.st) == finished ]] && exit "\$(cat \$Q/\$2.ex)"; sleep 0.2; done ;;
	log) cat \$Q/\$2.out ;;
	kill) kill -- -"\$(cat \$Q/\$2.pid)" 2>/dev/null ;;
	clean) rm -rf "$T/w/jobs/\$2" ;;
esac
EOF
printf '#!/usr/bin/env bash\ncat %s/journal 2>/dev/null\n' "$T" > "$T/wbin/journalctl"
chmod +x "$T/bin/"* "$T/wbin/"*
export PATH="$T/bin:$PATH"
R=$T/root/scripts/remote.sh
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
has() { if grep -q -- "$3" "$2"; then echo "ok   $1"; else echo "FAIL $1: no '$3' in:"; tail -n 8 "$2" | sed 's/^/       /'; fails=$((fails + 1)); fi; }
reboot() {   # the worker restarts: every job process dies, the queue is empty, a new boot id
	local p; for p in "$T"/w/q/*.pid; do [[ -e $p ]] && kill -- -"$(cat "$p")" 2>/dev/null; done
	rm -f "$T"/w/q/*; echo "boot-$RANDOM" > "$T/boot_id"
}

# 1. normal job: exit status, copy-back, state fetched, remote copy cleaned
"$R" -- bash -c 'mkdir -p build/test-all; echo result > build/test-all/x; exit 0' > "$T/o1" 2>&1; rc=$?
check "normal: exit 0, result copied back" "$rc $(cat "$T/root/build/test-all/x" 2>/dev/null)" "0 result"
check "normal: state fetched, worker recorded" "$(sed -n 's/^status=//p;s/^worker=//p' "$T"/root/build/remote/*.state | tr '\n' ' ')" "w fetched "
check "normal: remote copy cleaned" "$(ls "$T/w/jobs" | wc -l)" "0"
"$R" -- bash -c 'exit 4' > "$T/o1b" 2>&1; check "normal: a failing job's own status (4)" "$?" "4"

# 2. a worker that doesn't answer: nothing is sent, exit 255 fast; --check says so
touch "$T/down"; n0=$(cat "$T/w/q/n")
s=$SECONDS; "$R" -- true > "$T/o2" 2>&1; rc=$?
check "down at send: 255, quickly" "$rc $(( SECONDS - s < 30 ))" "255 1"
has "down at send: says so" "$T/o2" "does not answer (suspended, asleep or down?); nothing sent"
check "down at send: nothing queued" "$(cat "$T/w/q/n")" "$n0"
"$R" --check > /dev/null 2>&1; check "--check down: 255" "$?" "255"
rm -f "$T/down"
"$R" --check > /dev/null 2>&1; check "--check up: 0" "$?" "0"

# 3. stall: the worker goes away mid-job and comes back; the job passes
( sleep 1.5; touch "$T/down"; sleep 4; rm -f "$T/down" ) &
"$R" -- bash -c 'sleep 4; exit 0' > "$T/o3" 2>&1; rc=$?
wait
check "stall then recovery: exit 0" "$rc" "0"
has "stall: retried with backoff" "$T/o3" "unreachable"
has "stall: noticed the return" "$T/o3" "answers again after"

# 4. a reboot loses the job: no exit code, the queue forgot it -> JOB LOST 75
( sleep 1.5; reboot ) &
"$R" -- bash -c 'sleep 20' > "$T/o4" 2>&1; rc=$?
wait
check "reboot: JOB LOST (75)" "$rc" "75"
has "reboot: JOB LOST said, rerun" "$T/o4" "JOB LOST (worker preempted)"
has "reboot: why" "$T/o4" "no longer knows the job and it left no exit code"
echo boot-1 > "$T/boot_id"

# 5. a failure after a suspend (the runner logged a wall-clock gap) -> 75
"$R" -- bash -c 'echo "gap 100 400" >> .fb-gaps; exit 3' > "$T/o5" 2>&1; rc=$?
check "suspended then failed: 75" "$rc" "75"
has "suspended then failed: says why" "$T/o5" "exit 3 after it was suspended 1 time(s), 300 s in all"

# 6. a failure after the worker's memory shrank -> 75
"$R" -- bash -c 'printf "MemTotal:        8000000 kB\n" > '"$T"'/meminfo; exit 1' > "$T/o6" 2>&1; rc=$?
check "memory shrank then failed: 75" "$rc" "75"
has "memory shrank: says so" "$T/o6" "its memory shrank from 15625 to 7812 MB"
printf 'MemTotal:       16000000 kB\n' > "$T/meminfo"

# 7. an OOM kill with no sign of preemption stays JOB OOM-KILLED (137)
echo "job-x.service: Failed with result 'oom-kill'." > "$T/journal"
"$R" -- bash -c 'exit 1' > "$T/o7" 2>&1; rc=$?
rm -f "$T/journal"
check "OOM without preemption: 137" "$rc" "137"
has "OOM: said" "$T/o7" "JOB OOM-KILLED"

# 8. a passing job that saw a suspend passes, with a warning about its timings
"$R" -- bash -c 'echo "gap 100 200" >> .fb-gaps; exit 0' > "$T/o8" 2>&1; rc=$?
check "suspended but passed: 0" "$rc" "0"
has "suspended but passed: WORKER SUSPENDED" "$T/o8" "WORKER SUSPENDED"

# 9. the waiter dies while the worker is away; --attach waits for the worker and fetches the result
rm -f "$T"/root/build/remote/*
"$R" -- bash -c 'sleep 3; mkdir -p build/test-all; echo late > build/test-all/y; exit 0' > "$T/o9" 2>&1 & w=$!
sleep 1.5; touch "$T/down"; kill -9 $w 2>/dev/null; wait $w 2>/dev/null
( sleep 4; rm -f "$T/down" ) &
"$R" --attach-last > "$T/o9b" 2>&1; rc=$?
wait
check "attach across a suspend: exit 0, result fetched" "$rc $(cat "$T/root/build/test-all/y" 2>/dev/null)" "0 late"
has "attach: waited for the worker" "$T/o9b" "answers again after"

(( fails == 0 )) && echo "remote-selftest: all passed" || echo "remote-selftest: $fails failed"
(( fails == 0 ))
