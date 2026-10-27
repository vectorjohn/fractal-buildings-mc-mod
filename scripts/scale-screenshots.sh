#!/usr/bin/env nix-shell
#!nix-shell -i bash -p imagemagick
# Scale screenshots down to 720p high-quality JPEGs.
# Usage: scale-screenshots.sh [-o OUTDIR] FILE...   (OUTDIR defaults to "scaled")
set -euo pipefail

usage() { echo "Usage: $(basename "$0") [-o OUTDIR] FILE..." >&2; exit 1; }

outdir=scaled
while getopts "o:h" opt; do
  case $opt in
    o) outdir=$OPTARG ;;
    *) usage ;;
  esac
done
shift $((OPTIND - 1))
[ $# -gt 0 ] || usage

mkdir -p "$outdir"
for f in "$@"; do
  base=$(basename "$f")
  out="$outdir/${base%.*}.jpg"
  # Lanczos for sharp downscaling; 4:4:4 chroma avoids colour bleed on edges/text.
  magick "$f" -filter Lanczos -resize 1280x720 -strip \
    -quality 92 -sampling-factor 4:4:4 "$out"
  echo "$f -> $out"
done
