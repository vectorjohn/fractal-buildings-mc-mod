# CFGEXAMPLE: the example config stays in step with the code

- Scope after John's cut: no runtime change. `FbConfig.ensureFile` still writes `FbSettings.defaults().toJson()` at mod initialisation; `docs/release/fractalbuildings.example.json` stays where it is, one copy. (An earlier attempt that made the mod write the example was reverted.)
- `FbSettingsExampleTest` (planner-core) reads the file from `../docs/release/` (tests run in `planner-core/`; `planner-core/build.gradle` lists it as a test input): parses to `FbSettings.defaults()` with no warnings; non-"//" keys equal the `toJson()` keys; a `// <key>` note per key; the "// styles" note lists exactly `StyleLibrary.ids()`.
- Mutation: adding a key to `toJson()` fails `exampleHasExactlyTheKeysToJsonWritesEachWithANote`.
- README-player Config: added one sentence to copy the example to `config/fractalbuildings.json` before launching. John's own rewrite of that section was not on the run branch at the time; a merge conflict there is possible, keep his wording and the one sentence.
