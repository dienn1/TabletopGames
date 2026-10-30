# JSON-Bag

JSON-Bag represents a game position as a bag of tokens taken from its JSON serialisation, then compares two positions with the Jensen-Shannon distance (JSD). This package holds the tokeniser, the distance, a value function built on prototype bags, and a one-step look-ahead player that uses that value function.

The point of going through JSON is that the same code works for any game, as long as the game can produce a JSON snapshot of its state. Nothing here knows the rules of a particular game.

## Pipeline

```
AbstractGameState  ->  XGameStateContainer  ->  Gson JSON  ->  tokens  ->  Map<String,Integer>
```

The container is the piece you write per game. Everything downstream is game-agnostic.

## Files

| File | What it does |
|---|---|
| `Tokenizer` | Turns an object, a JSON string, or a game state into a token frequency map. Loads prototypes and filter lists from disk. |
| `TokenDictionary` | Interns token names to small integer ids; the tokeniser hands it a running hash. |
| `TokenBag` | The bag: counts in a dense `int[]` indexed by token id, with a `Map<String,Integer>` view. |
| `PrototypeSet` | The prototypes compiled into one interleaved probability array for the distance loop. |
| `JensenShannonDistance` | `normalize` and `jsd` over two frequency maps, plus a fast path over a compiled prototype set. JSD is symmetric and bounded by 1. |
| `JSONBagHeuristic` | `IStateHeuristic` that scores a state by JSD to one prototype bag per player. |
| `JSONBagOSLAPlayer` | One-step look-ahead: rolls each legal action forward, scores the result with `JSONBagHeuristic`, plays the best. |
| `GameStateContainerFactory` | Maps a `GameType` to the container class that snapshots it. Throws if the game has no container. |
| `TokenizerTest` | Scratch harness, not a JUnit test. It lives in `src/main/java`, reads `test.txt` and `filterTest.json`, and does not run under surefire. |

## Quick start

For a game that already has a container (`CantStop`, `Catan`, `Connect4`, `Dominion` and its two variants, `DotsAndBoxes`, `SeaSaltPaper`, `Uno`, `Wonders7`):

```java
// One prototype bag per player. The bundled folders hold four, one per seat of a 4-player game;
// load as many as your game has players, because evaluateState throws on a mismatch.
// Prototype folders are produced by your own runs and are not checked in; see the experiment section.
List<Map<String, Integer>> prototypes = Tokenizer.loadPrototypes(
        "Experiments/valueFuncTest2/CantStop/prototype100_0_allSegFS_seg4_cum", 4, true);
JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);

heuristic.resetCurrentBag(gs);                        // start the running bag at the current state
double value = heuristic.evaluateState(gs, playerId); // 0 when the player has lost, 1 when they have won
```

To play with it:

```java
JSONBagOSLAPlayer player = new JSONBagOSLAPlayer(prototypes, List.of());  // empty filter = keep every token
player.setForwardModel(game.getForwardModel());  // not needed inside Game, which sets it for you
```

`JSONBagOSLAPlayer` accumulates tokens as the game runs, so call `initializePlayer(gs)` before it acts, which `Game` also does for you. Its `_getAction` rolls each candidate through `getForwardModel()`, so a player built outside a `Game` throws `NullPointerException` until the forward model is set. For a game with simultaneous turns, set `player.setSimultaneousMode(true)` before the game starts; that makes each candidate roll to the end of the round rather than a single action.

## Adding a new game

### 1. Write the container

Create `games.<game>.<Game>GameStateContainer` next to the state class. It extends `AbstractGameStateContainer` and copies the fields you want in the token bag. CantStop's is the smallest complete example:

```java
public class CantStopGameStateContainer extends AbstractGameStateContainer {

    protected boolean[] completedColumns;
    protected int[][] playerMarkerPositions;
    protected List<Dice> dice;

    public CantStopGameStateContainer(CantStopGameState gs) {
        super(gs);
        completedColumns = gs.completedColumns;
        playerMarkerPositions = gs.playerMarkerPositions;
        dice = gs.dice;
    }
}
```

What to include: decks, hands, counters, board occupancy, scores, and phase flags that describe the position.

What to leave out: the game parameters, the action history, timers and the extended-sequence stack. The base class already excludes those, and repeating them adds tokens that say nothing about the position and cost time to serialise. `CatanGameStateContainer` shows both sides: it copies scores, resources, tokens, dev cards and the trade offer, while exchange rates, `developmentCardPlayed` and `nTradesThisTurn` are commented out.

Any field of the container is tokenised, so the container is also your control over what the bag can see. If a field is needed by the game but not by the bag, leave it out.

### 2. Register it in the factory

```java
// in GameStateContainerFactory's static block
containerFactories.put(GameType.Foo, gs -> new FooGameStateContainer((FooGameState) gs));
```

Lookup is by exact `GameType`, so a game with variants needs one line per variant. Dominion does this for `DominionSizeDistortion` and `DominionImprovements`, both pointing at `DominionGameStateContainer`.

`GameStateContainerFactory.hasContainer(gameType)` answers whether a game is wired up.

### 3. Make sure the container serialises

`Tokenizer` serialises the container with Gson. A field that Gson cannot write fails at runtime, and the most common failure is a name collision inside a component:

```
java.lang.IllegalArgumentException: class games.uno.cards.UnoCard declares multiple JSON fields named type
```

`Component` already declares `type`, so a subclass field of the same name cannot be serialised as well. The fork renames the subclass field instead of the parent: `UnoCard.type` became `unoCardType`, `DominionCard.type` became `cardType`, `Wonder7Card.type` became `buildingType`, `Dice.type` became `diceType`. Rename the subclass field and everything that reads it; do not add a Gson exclusion, because the parent's `type` is what tells the tokeniser whether a node is a card, deck or token.

A quick check before going further:

```java
AbstractGameStateContainer container = GameStateContainerFactory.createContainer(gs);
System.out.println(new Gson().toJson(container));
```

### 4. Look at the tokens

Tokenise a fixed position and read a few entries:

```java
Map<String, Integer> bag = Tokenizer.tokenize(gs);
bag.entrySet().stream()
   .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
   .limit(20)
   .forEach(System.out::println);
```

The keys carry the path to the value. From real states:

```
.completedColumns[0].false                       -> 1      indexed position
.completedColumns.false                          -> 13     the same value without the index (13 slots, one per track)
.cardsIncludedInGame.COPPER                      -> 32     from the numeric frequency rule
.cardsIncludedInGame.COPPER.32                   -> 1      the same field as a plain value
.playerDrawPiles.components.cardType.COPPER      -> 7
.currentCard.unoCardType.Skip                    -> 1
```

With `mode=both`, each atomic value is emitted twice, once with array indices and once without, so expect roughly two tokens per value. For scale, a fresh 2-player game gives 133 tokens for CantStop, 384 for Dominion and 945 for Uno.

Check that you see the entities you meant to include, that the counts are stable across two runs of the same game, and that noisy fields stay out. Key names come from the game's own state, so two games with different names produce different bags even when the positions are equivalent.

### 5. Add a test

Put it in `src/test/java/players/jsonBagPlayers/`, next to `JSONBagOSLAPlayerTest`. Assert on tokens you expect, not on the whole bag, so small changes to the container do not break the test for no reason:

```java
Map<String, Integer> bag = Tokenizer.tokenize(state);   // a fresh 2-player CantStop game
assertEquals(Integer.valueOf(1), bag.get(".completedColumns[0].false"));
assertEquals(Integer.valueOf(13), bag.get(".completedColumns.false"));
```

Run it with:

```bash
mvn test -Dmaven.test.skip=false -Dtest=JSONBagTokenizerTest
```

The pom sets `maven.test.skip=true`, so the flag is required.

## Prototypes and filters

A prototype is a bag of tokens produced by one player's trajectories. `Tokenizer.loadPrototypes(dir, n, full)` reads `prototype0.json` to `prototype<n-1>.json`, or `full_prototype<i>.json` when `full` is true; both are plain `Map<String, Integer>` documents.

`JSONBagHeuristic` needs exactly one prototype per player, because `evaluateState` returns the entry for the player it is asked about:

```java
if (gs.getNPlayers() != prototypes.size()) throw new RuntimeException("PLAYER COUNT AND PROTOTYPE COUNT MISMATCHED: ...");
```

A filter list is applied as a whitelist by the heuristic: `Tokenizer.tokenize(gs, filterSet, true)`. An empty list means no filtering. `Tokenizer.loadStringList(path)` reads a JSON array of token strings, which is the format `features-top500.json` uses in the experiment folders.

## Tokeniser options

The full signature is `Tokenizer.tokenize(obj, prefix, ordered, mode, numSuffixFrequency, filterPlayer, binning, pairXY, filterSet, whitelist)`.

| Parameter | Effect |
|---|---|
| `prefix` | Prepended to every token produced for this node. |
| `ordered` | Include array positions (`[0].`, `[1].`). Turn it off when the order of a list carries no meaning. |
| `mode` | `both` emits ordered and unordered tokens, `ordered` and `unordered` narrow that, `char` throws `NotImplementedException`. |
| `numSuffixFrequency` | For a positive integer value, add the key with that integer as its count, so `"gold": 30` contributes `gold` 30 times. Integers only; decimals are ignored. |
| `filterPlayer` | Skip any object with a `player` field greater than 0. Useful when only the acting player's view matters. |
| `binning` | Bucket `x` and `y` values into multiples of 2. |
| `pairXY` | Combine `x` and `y` into one token (`.x.3.y.5`) instead of two. |
| `filterSet`, `whitelist` | Keep only the listed tokens, or drop them. |

The convenience overloads use `mode=both`, `numSuffixFrequency=true`, no binning, no pairing and no filter. They differ in one respect: `tokenize(Object)` and `tokenize(AbstractGameState)` pass `ordered=true`, which is the path the heuristic and the player take, while `tokenize(String json)` passes `ordered=false` and so emits only the unindexed form at the top level.

## Running the experiments

`core.TAG` dispatches to the driver:

```bash
java -jar target/TAG.jar JSONBagValueFunctionExperiments game=DotsAndBoxes n=1 nPlayers=2 \
     experimentRootPath=Experiments/valueFuncTest2/DotsAndBoxes
```

Arguments: `game` (`GameType` name, default `Dominion`), `n` (games per seed, default 100), `nPlayers` (default 4), `topK` (default 500), `includeNone` (default false), `experimentRootPath` (default `Experiments/valueFuncTest2/<game>`).

The root holds one folder per prototype set and one per feature selection; the driver walks every subfolder and uses each as both roles:

```
<root>/<prototypesA>/full_prototype0.json ... full_prototype<nPlayers-1>.json
<root>/<featuresB>/features-top500.json
<root>/results/jsonbag_pair_statistics.csv
```

Folders whose name contains `singleFS` are skipped, and a folder with no `features-top<K>.json` is skipped unless `includeNone` is set, which adds a run with no filter. Each combination runs once with the JSON-Bag player in each seat, the other seats filled by `OSLAPlayer` (`SimultaneousOSLAPlayer` and `setSimultaneousMode(true)` for Wonders7). Results go to a new CSV under `results/`; an existing file is never overwritten, the driver adds `_1`, `_2` and so on.

The prototype and feature folders are generated by your own runs and are not in the repository. Keep them under `Experiments/valueFuncTest2/<game>` on disk, which is where the driver looks by default, or pass `experimentRootPath`.

## Performance

The package was rewritten for speed without changing what a bag contains. Every step below is behaviour-preserving:
the token sets, the distances and the heuristic values are identical to the previous implementation up to
floating-point summation order (measured maximum difference 3.6e-14 over 12 tokeniser configurations, 6 games and
120 states per game; see the equivalence harness below). The exception is an ill-conditioned value function: when a
whitelist leaves a prototype sharing almost no mass with the bag, `1 - distance` is a difference of two nearly equal
numbers and the normalisation amplifies it — up to 1.1e-11 was measured on a ten-token whitelist, with a median of
5e-15. That is conditioning, not a logic difference, but it is outside the bound quoted above.

Where the time went, and what changed:

| Change | What it removes |
|---|---|
| Tokens are interned to integer ids and counts live in a dense `int[]` | `HashMap<String,Integer>` lookups, boxing, and a `HashMap` copy per evaluated state |
| The tokeniser walks the Gson tree once, writing into a char buffer with a running hash | intermediate token maps and a merge at every level of nesting, and a second pass to hash each token |
| Numbers are appended digit by digit, never via `Number.toString()` | a `String` allocation per numeric value |
| The dictionary probes on a 64-bit FNV-1a hash | the 50-character equality check on every hit |
| `jsd` only walks the bag's non-zero tokens, not the prototype's whole vocabulary | tens of thousands of hash lookups per distance |
| The pairwise divergence is written `(p+q)(1-log2(p+q)) + p·log2(p) + q·log2(q)` | one logarithm per shared token instead of two, with `p·log2(p)` precomputed |
| Prototypes are one interleaved array, `[p, p·log2(p)]` per token per prototype | four random cache misses per token became one |
| Evaluating a state merges it into the trajectory bag and rolls the merge back | copying the whole bag for every candidate action |
| The tokeniser skips any path no whitelisted token can reach | building and interning tokens the filter then rejects: 64.5% of Dominion's emissions, 46.4% of Wonders7's and 29.9% of SeaSaltPaper's, against 0.4-4.5% of CantStop, Connect4 and DotsAndBoxes, whose whitelists already cover almost everything |
| The numeric-frequency token is built from the `Number` the value emission already holds | a second instanceof chain over the same `JsonElement` for every atomic value; that pass was 17-37% of `evaluateState` on CantStop, DotsAndBoxes and Wonders7 |

Pruning is the one entry above that only applies when the configuration has a whitelist. A token no whitelisted token can
reach is rejected however it is built, so the walk asks - at every point where it could extend the path - whether some
whitelisted token has that path as a prefix, and skips the value, the intern probe, the bag add and the whole subtree
when none does. The set is built once from the whitelist and holds every whitelisted token plus every prefix ending just
before a `.` or a `[`, the only positions a path is extended from; it is a single `int[]` of 32-bit token hashes, 16 kB
at the sizes these games use. A 32-bit collision can only make the test say "live" where it is not, which costs one
token built and rejected as before - it can never drop a token the filter would have kept. With no whitelist, or one
whose prefixes would not fit in 16k slots, nothing is built and the walk is unchanged.

On the six paper games it measures Dominion −22.8% (p=0.031 over six paired runs, every rep faster), SeaSaltPaper
−5.2%, CantStop −2.6%, Connect4 −2.2%, DotsAndBoxes −2.2% and Wonders7 −2.1%, COMBINED −4.2% (p=0.031), with no game
slower. The size of the win tracks the "dead emissions" column below: Dominion's whitelist covers a fifth of what its
states emit, the other five games' whitelists cover 70-99%.

Whole games, one JSON-Bag OSLA player against OSLA opponents, best of three seeds, same harness for both builds
(`JSONBagBench game 3`). Whole-game wall clock is noisy for a reason that is not measurement error: the player
shuffles its action list with the global `Random`, so successive runs play different games of different length (the
"actions" column moves between 238 and 342 for CantStop alone). The ranges below are two full runs of each build:

| Game | Before (ms/game) | After (ms/game) | Speed-up |
|---|---|---|---|
| CantStop | 140-153 | 28-41 | 3.7-5.0x |
| Connect4 | 12-34 | 10-11 | 1.1-3.4x |
| DotsAndBoxes | 94 | 60-76 | 1.2-1.6x |
| Dominion | 1638 | 82-121 | 14-20x |
| SeaSaltPaper | 653 | 62-68 | 9.6-10.5x |
| Wonders7 | 2140 | 92-136 | 16-23x |

Cost relative to vanilla OSLA fell from 3.9x-194x to 1.5x-9x on the same runs. Connect4 and DotsAndBoxes barely move
because their states are tiny: almost all of their game time is the state copy and the forward-model step that OSLA
already pays, which is outside this package.

End to end through `core.JSONBagValueFunctionExperiments`, the driver the papers use (it loads every prototype set and
plays JSON-Bag OSLA against OSLA): Dominion with `n=10` went from 65.2 s to 25.8 s, Wonders7 with `n=3` from 16.9 s
to 9.0 s, CantStop with `n=2` from 2.7 s to 1.8 s. Those ratios are smaller than the per-call ones because the
driver's own setup and the unchanged OSLA side are inside the total.

Per-call cost, best of 21 passes over 220 states (`JSONBagBench legacy`, which uses only API both builds expose):

| Game | tokenise before | tokenise after | `evaluateState` before | `evaluateState` after | speed-up |
|---|---|---|---|---|---|
| CantStop | 36.6 us | 34.8 us | 234 us | 30.2 us | 7.7x |
| Connect4 | 75.3 us | 47.5 us | 143 us | 46.8 us | 3.1x |
| DotsAndBoxes | 10.3 us | 9.9 us | 50.4 us | 10.4 us | 4.8x |
| Dominion | 390 us | 145 us | 4365 us | 155 us | 28x |
| SeaSaltPaper | 221 us | 93 us | 3914 us | 133 us | 29x |
| Wonders7 | 593 us | 236 us | 14928 us | 336 us | 44x |

Those tokenise columns rebuild the dictionary on every call, which is what `Tokenizer.tokenize(...)` does. Inside the
heuristic the dictionary is warm, and a state costs less: with a warm dictionary, tokenising a Wonders7 state is
171 us rather than 238 us, a Dominion state 113 us rather than 153 us (`JSONBagBench timing`).

A 4,096-entry front cache in front of the dictionary was tried and removed: at that size it costs a 64 kB array
clear on every fresh dictionary, which showed up as a third of CantStop's tokenise time, and the main table's own
probe is cheap enough that the cache never paid it back.

Two costs now dominate and are close to their floor: interning a token into a 45k-entry vocabulary (about 30 ns,
mostly cache misses) and one `Math.log` per shared token per prototype (about 5 ns, and `Math.log` is a hardware
intrinsic - a table-plus-series replacement was measured to be no faster).

### Measuring on a hybrid-core machine

The numbers in the tables above were taken before the harness pinned its process, and two effects on this class of
machine are large enough to swamp any code change. Windows migrates an unpinned thread onto an E-core after about two
seconds of sustained work, where this workload runs 5.7x slower (Wonders7 `evaluateState`: 179 us pinned to a P-core,
1030 us on an E-core). And a benchmark that starts timing before the intern table has seen the corpus reads the same
call 3x faster than its steady state, because the first pass is the one that fills the dictionary.

`JSONBagBench warm` now runs one full pass over the corpus before timing, reports the steady-state figure, and the
rounds are timed one game per JVM with the process pinned to a single P-core
(`agent_scripts/jsonbag_ab/pinned.sh <cpu>`, and `ab.py`, which sets the affinity on the JVM it spawns). Under that
protocol `warm` repeats to about 1% within a session. The rounds run so far, decided by whichever phase a fresh
measurement said dominated rather than by a list of candidate ideas, are recorded in `.artifacts/jsonbag/ledger.md`.

Eliminating the second numeric-type pass is the one that paid: CantStop −4.3% (p=0.031 over six paired runs),
Wonders7 −3.3% (p=0.094), the other four games unchanged. A change that kept rejected tokens out of the intern table
shrank Wonders7's dictionary from 18,905 entries to 1,322 and measured neutral, so it was reverted — the probe is
not bound by table size, and paying one extra probe per emission costs about what a smaller table saves.

### Edge cases the playouts do not reach

Four cases were checked directly against the old build, because no game playout hits them:

- Numbers at the edge of the range (`Integer.MIN_VALUE`, `Long.MIN_VALUE`). The digit loop works in `long`: negating
  an `int` minimum overflows back to itself, which printed `-0` before this was fixed.
- An empty prototype. The old `jsd` returns 1 for any distance involving an empty distribution, whatever the other
  side holds; the dense path reproduces that. Without the special case an empty prototype sat at 0.707 and the value
  function returned 0.5 where it used to return 1.
- A prototype whose map holds only zero counts. The old code asked `Map.isEmpty()`, so such a prototype counts as
  present even though it carries no mass; the compiled form keeps each prototype's entry count for this test.
- Re-entrant recording. `TokenBag.startRecording()` nests and each `rollback()` undoes its own additions, so a value
  function evaluated inside another recording still unwinds in order.
- A numeric frequency past the int range. `numericFrequency` counts a positive integer by its value, and reading that
  value as an int truncates anything past 2^31 (9223372036854775807 comes out as -1). The old build stored the
  negative count, which corrupts the bag's total and made the distance loop compute `0 * log2(0)` = NaN once such
  counts cancelled; this build drops the contribution instead. Every value that fits an int is unaffected.
- A token held at zero. `TokenBag.add` removes an id from the non-zero list when its count reaches zero, and the
  distance loop skips non-positive counts, so a zero can never reach a logarithm.

### Checking that an optimisation changed nothing

`JSONBagEquivalence` writes a fingerprint of the token maps (12 tokeniser configurations), the JSD values and the
heuristic values along a seeded playout of each game. Run it with the old build and the new build, then diff:

```bash
java -cp "target/classes;target/test-classes;target/dependency/*" \
     players.jsonBagPlayers.JSONBagEquivalence .artifacts/jsonbag/equiv-new
python .artifacts/jsonbag/compare.py .artifacts/jsonbag/equiv-old .artifacts/jsonbag/equiv-new
```

`TokenizerGoldenTest` is the faster guard: 84 token digests (14 documents under 6 tokeniser configurations) captured
from the build that predates the rewrite. It needs no prototype folders, so it runs anywhere, and it fails on any
change to what a token is.

Whitelist pruning has its own invariant, checked two ways. `JSONBagBench wlstats` compares
`tokenize(gs, whitelist, true)` with the unfiltered bag restricted to the whitelist over 40 states per game and reports
`bag-mismatched-states`; it also splits the emissions into accepted, rejected under a live path and rejected under a
dead path, which is the measurement that decides whether pruning is worth anything on a given game.
`JSONBagTokenizerTest.whitelistFilteringEqualsTheUnfilteredBagRestrictedToTheWhitelist` asserts the same invariant on a
real CantStop container, for the whole token set and for a subset that forces pruning; the nested-array case it also
covers is not reached by the 25-token whitelist the fingerprint harness builds.

In the equivalence sweep, token digests must match exactly. Distances and values are compared numerically;
the accepted difference is the summation order of the divergence, at the 1e-14 level. Add
`-Djsonbag.verifyTokens=true` to make the dictionary compare token characters as well as their hashes - the
classifier is trusted only after that run has agreed with the baseline, which is how the token identity by 64-bit
hash was validated.

The playout tokenises the *live* game state on purpose: several games' `copy()` produces states whose JSON differs in
list order from run to run, which would make a fingerprint of stored copies irreproducible.

## Limitations worth knowing

- `JSONBagOSLAPlayer._getAction` shuffles the legal actions with the global `Collections.shuffle(actions)` before scoring, so a seeded run can still pick different actions between ties. Seed the shuffle through `rnd` if you need reproducible runs.
- A bag that shares no tokens with any prototype is a degenerate input; `subtractNormalized` throws `IllegalStateException` with the distances rather than returning `NaN`.
- The container is a hand-written snapshot. When the game state gains a field you want tokenised, add it to the container as well, or the bag silently ignores it.
- The tokeniser walks whatever Gson produces, so a field with an unstable iteration order (a `HashMap` keyed by objects without a stable `hashCode`) shows up as noisy tokens. Prefer lists and `LinkedHashMap`/enum-keyed maps in containers.

