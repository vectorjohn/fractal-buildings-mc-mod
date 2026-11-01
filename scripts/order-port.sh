# Sourced by scripts/order-test.sh (ORDERPORT): free-port choice for the order-test servers. Needs $OUT (claims go in $OUT/ports/<port>).
port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
# prints the first port >= $1 that nothing listens on and that no other run of this script claimed (mkdir is the atomic claim)
pick_port() {
	local p="$1"
	while :; do
		if mkdir "$OUT/ports/$p" 2>/dev/null; then
			if ! port_busy "$p"; then echo "$p"; return; fi
			echo "order-test: PORT RETRY: $p already in use (checked before start)" >&2
		fi
		p=$((p + 1))
	done
}
