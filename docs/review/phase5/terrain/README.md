# Terrain response (TR, 5.3)

Plan views (zoomed, cyan = quay) and one 3D district overview per preset in `viewer/city-presets/` (`tr-coast`, `tr-river`, `tr-steep`).
Regenerate: `cd viewer && nix develop -c npm run city -- tr-coast tr-river tr-steep` then
`FB_CITIES=tr-coast,tr-river,tr-steep CITY_GALLERY=1 nix develop -c npx playwright test e2e/city.spec.ts` (after `npm run build`).

- `tr-coast-plan.png`, `tr-coast-dm47_m22-overview.png`: a coastal city; the promenade (grey paving in 3D) runs along the shore with shops facing it.
- `tr-river-plan.png`, `tr-river-d7_m59-overview.png`: a lake town; quay around the lake, a plank bridge (role `deck`) over the water.
- `tr-steep-plan.png`, `tr-steep-d42_m47-overview.png`: badlands; arterials no longer run over ground a road cannot be graded on, the main road cuts a ramp.
