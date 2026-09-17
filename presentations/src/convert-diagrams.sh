#!/usr/bin/env bash
# Convert the tutorial's SVG diagrams to PNGs for the slide decks.
#
#   bash convert-diagrams.sh              # every diagram
#   bash convert-diagrams.sh 01-order-flow 26-feature-flags   # only these
#
# Output is normalised to WIDTH px. That matters: the SVGs carry a viewBox but
# no width, so LibreOffice picks its own size and the raw conversion comes out
# at whatever that happens to be -- which is how the committed set ended up
# uniformly 1920 wide while a later re-run produced 1426-1843. Slides want one
# consistent resolution, so pin it here rather than hoping.
set -euo pipefail
cd "$(dirname "$0")"

SVGDIR="../../assets/diagrams"
WIDTH="${DIAGRAM_WIDTH:-1920}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p png

# ImageMagick 7 renamed `convert`; fall back for IMv6.
IM="$(command -v magick || command -v convert)"

if [ $# -gt 0 ]; then for n in "$@"; do cp "$SVGDIR/$n.svg" "$TMP"/; done
else cp "$SVGDIR"/*.svg "$TMP"/; fi

( cd "$TMP" && soffice --headless --convert-to pdf --outdir . ./*.svg >/dev/null 2>&1 )

for pdf in "$TMP"/*.pdf; do
  n="$(basename "${pdf%.pdf}")"
  pdftoppm -png -r 200 "$pdf" "$TMP/p_${n}" >/dev/null 2>&1
  "$IM" "$(ls "$TMP/p_${n}"*.png | head -1)" \
    -background white -flatten -trim +repage \
    -bordercolor white -border 24 -resize "${WIDTH}x" "png/${n}.png"
done

echo "Converted $# diagram(s) to PNG at ${WIDTH}px wide" 2>/dev/null || true
ls png/*.png | wc -l | xargs echo "PNGs on disk:"
