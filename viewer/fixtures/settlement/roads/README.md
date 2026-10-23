# Settlements with road links (ROADMAP 6.10)

Skeleton plans with required exits and approaches toward their road links (ADR 0004 N5), for the plan view's `links` layer and `e2e/roads.spec.ts`.
World 12345, recorded terrain `fixtures/terrain/world-12345-s64.json.gz`, cell size 1024:

```sh
fractal-cli city --seed 12345 --terrain recorded:fixtures/terrain/world-12345-s64.json.gz --cell 2,1 --roads --out-dir viewer/fixtures/settlement/roads/city-s2_1
fractal-cli city --seed 12345 --terrain recorded:fixtures/terrain/world-12345-s64.json.gz --cell 3,7 --roads --out-dir viewer/fixtures/settlement/roads/town-s3_7
```

`city-s2_1` is a two-cell city (r964) with three links (one a meeting point); `town-s3_7` a town (r429) with two. `LinkExitsTest` plans the same cells.
