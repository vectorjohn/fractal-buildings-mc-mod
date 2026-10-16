/**
 * Building IR, schema {@code fb-scene/1} (docs/IR.md, docs/plans/phase3.md "IR v1").
 *
 * <p>Two levels, matching the pure-cache contract (docs/research/pool/recommendation.md, ADR 0003
 * D6): a {@link dev.fractalbuildings.core.ir.BuildingIr} is the site-independent shell, a pure
 * function of its {@link dev.fractalbuildings.core.ir.BuildingSpec} (cacheable and poolable by
 * spec hash); a {@link dev.fractalbuildings.core.ir.PlacedBuilding} puts a shell on lots with an
 * origin, a rotation and site boxes (foundations). A {@link dev.fractalbuildings.core.ir.SceneDoc}
 * holds lots, placed buildings, an optional terrain patch and invariant violations; later phases
 * add districts and roads to it.
 *
 * <p>Rules: integers only (no floating point anywhere in the IR); 64-bit values are written as
 * decimal strings in JSON; lists are in generation order and that order is part of the output.
 */
package dev.fractalbuildings.core.ir;
