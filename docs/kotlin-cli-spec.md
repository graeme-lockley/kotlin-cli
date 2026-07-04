# kli — Kotlin CLI Tool Specification

> A zero-ceremony CLI tool for running, testing, building, and publishing Kotlin projects.
> Think: `cargo` for Kotlin, or `flue` for the JVM.

---

## 1. Philosophy

Kotlin is a great language burdened by terrible project tooling. Gradle and Maven require DSLs, XML, lifecycle plugins, and 15+ lines of config before you can run a hello world with a dependency. This tool eliminates all of that.

**Core principles:**

- **Zero boilerplate.** A `project.json` with one field (`deps`) is the maximum config needed.
- **Convention over config.** `fun main()` discovery. `*Test.kt` for tests. Any directory structure works.
- **One command to run.** `kli run tools.CLI` compiles stale sources and runs. That's it.
- **Dependencies are a first-class concern.** Inline in `project.json`, resolved from Maven Central automatically.
- **Clean project directory.** The project directory contains only source code. Nothing else. No build artifacts, no cache directories, no generated files.
- **Sensible defaults everywhere.** If a field isn't in `project.json`, the tool picks a reasonable default.

---

## 2. Commands

### 2.1 `kli run [--show-compiler-logging] [--silent] [--verbose] <qualified-name>`

Compiles all stale source files in the project, then runs the specified main class.

```
kli run tools.Server
kli run scripts.Migrate
kli run services.MyApp
kli run --show-compiler-logging tools.Server
kli run --silent tools.Server
kli run --verbose tools.Server
```

The `<qualified-name>` maps to a source file by convention:

| Qualified name | Source file |
|---|---|
| `tools.Server` | `<root>/tools/Server.kt` |
| `scripts.Migrate` | `<root>/scripts/Migrate.kt` |
| `services.MyApp` | `<root>/services/MyApp.kt` |

Mapping rule: `<qualified-name>` is the relative file path from a configured source root, replacing `/` with `.` and removing `.kt` (case-sensitive).

**Behaviour:**
1. Walk up from cwd to find `project.json` (project root)
2. Resolve Maven dependencies (cached in `~/.kli/m2/`)
3. Recompile only stale source files (hash-tracked)
4. Run `fun main()` from the specified class

**Entrypoint expectation (v1):**
- The qualified name maps to a Kotlin source file and currently targets top-level `fun main(...)` entrypoints.
- Runtime invocation resolves to generated Kotlin file classes using the `Kt` suffix (for example, `tools.Server` -> `tools.ServerKt`).
- Object/class member mains are out of scope for v1 and should use top-level `main`.

**Arguments after `--` are forwarded to the program:**

```
kli run tools.Server -- --port 8080 --db-url jdbc:postgresql://localhost/mydb
```

**Debugging compiler diagnostics:**
- Add `--show-compiler-logging` to print Kotlin compiler diagnostic logging during compilation.
- Default behaviour keeps compiler diagnostic logging quiet unless there is an error.

**Progress output:**
- By default, kli prints light-gray progress lines with elapsed durations for dependency downloads and compile work.
- Example:

```
Compiling format.test.kt (53ms)
Downloading com.google.code.gson:gson:2.13.1 (18ms)
```

- Use `--silent` to hide these progress lines.

**Verbose runtime diagnostics:**
- Use `--verbose` to print execution diagnostics before launch, including project root, resolved main class, source count, runtime dependency count, compile classpath, and runtime classpath.
- `--verbose` also prints full stack traces for command-level errors.

**Shebang support:**
Since Kotlin's lexer discards `#!` at the top of a file, source files with a shebang work directly:

```kotlin
#!/usr/bin/env kli run
fun main() {
    println("Hello!")
}
```

Make it executable (`chmod +x`) and run as `./tools/CLI.kt`.

### 2.2 `kli test [--show-compiler-logging] [--silent] [path]`

Discovers and runs tests authored with `kotlin.test` annotations. Without arguments, discovers all `*Test.kt` files under configured source roots.

```
kli test                           # run all tests in the project
kli test tools/SomeServiceTest.kt  # run tests in a single file
kli test tools/                    # run tests in a directory
kli test --show-compiler-logging   # include compiler diagnostic logging
kli test --silent                  # hide compile/dependency progress lines
```

**Behaviour:**
1. Discover matching test files (glob: `**/*Test.kt`)
2. Generate a synthetic top-level test runner file that bundles all discovered tests
3. Compile and execute via a generated JUnit 5 runner that invokes tests written with `kotlin.test`
4. Report results to stdout with class-level summaries, attributed stdout lines, and detailed failure locations
5. Exit code 0 = all pass, non-zero = failures

**Test output format (summary-first):**
- Class-level summary lines (fully-qualified class name):

```
tool.HelloTest (✓2 9ms)
tool.AddTest (✗1 10ms)
```

- Per-test stdout/stderr attribution prefix:

```
tool/HelloTest.kt tool.HelloTest testHello | hello
```

- Final compact total line:

```
2 test file(s), 2 passed (133ms)
2 test file(s), 1 passed, 1 failed (133ms)
```

- Failure details include class + test name and file:line location:

```
Fail 1: tool.HelloTest.testHello
  at tool/HelloTest.kt:9
  expected: <true> but was: <false>
```

Notes:
- Counts with value `0` are hidden where possible to reduce noise.
- Location and attribution prefixes are rendered in light gray for readability.

**Why generate a synthetic runner if JUnit is used?**
- Keeps test orchestration in kli (discovery, filtering, reporting)
- Uses JUnit only as the execution backend
- Preserves simple CLI behavior without exposing JUnit internals

### 2.3 `kli clean`

Removes all cached artifacts for the current project (compiled classes, generated test runners, hash manifests).

```
kli clean
```

**Behaviour:**
1. Compute project root hash from full absolute path
2. Delete `~/.kli/cache/<project-root-hash>/`
3. Leaves `project.json`, source files, and the global Maven cache (`~/.kli/m2/`) untouched

> Safe to run at any time. Subsequent `kli run` or `kli test` will recompile from scratch.

### 2.4 `kli clean-all`

Removes ALL cached project artifacts globally.

```
kli clean-all
```

**Behaviour:**
1. Delete the entire `~/.kli/cache/` directory
2. Leaves `~/.kli/m2/` (global Maven cache) untouched

### 2.5 `kli init [name]`

Scaffolds a new project in the current directory or a named subdirectory.

```
kli init my-project
```

Creates:

```
my-project/
├── project.json          # { "name": "my-project", "version": "0.1.0", "deps": [], "target": "21" }
├── tools/
│   └── CLI.kt           # fun main() = println("Hello from my-project!")
└── .gitignore
```

### 2.6 `kli project-lint`

Validates `project.json` strictly — reports unknown fields and type errors. Exits with code 1 if anything is wrong.

```
kli project-lint
```

**Behaviour:**
1. Parse `project.json`
2. Validate against the schema
3. Report all unknown fields and type mismatches with line/column info
4. Exit 0 if valid, 1 if invalid

### 2.7 `kli refresh`

Re-resolves all dependencies, including SNAPSHOT versions.

```
kli refresh
```

**Behaviour:**
1. Clear the per-project dependency lock/resolution cache
2. Re-resolve all Maven dependencies
3. Recompile all source files (cache invalidated)

### 2.8 `kli package [--output <path>] [--show-compiler-logging] [--silent]`

Builds a distributable fat JAR with all discovered mains and installs it into the local Maven repository cache (`~/.kli/m2/`).

```
kli package --output ./dist/my-project.jar
kli package --show-compiler-logging
kli package --silent
```

**Behaviour:**
1. Resolve all dependencies
2. Compile all source files
3. Assemble a fat JAR (no shading/relocation in v1)
4. Use a dispatcher `Main-Class` (`kli.dispatcher.MainDispatcherKt`) that accepts `<qualified-name>` as the first runtime argument
5. Include all resources matched by patterns in `project.json` → `resources`
6. Output to `--output` path (default: `./dist/<name>-<version>.jar` using name and version from `project.json`)
7. Install the artifact and metadata into `~/.kli/m2/` using a local default group (`io.kli.local`) and write:
  - `<artifact>-<version>.jar`
  - `<artifact>-<version>.pom`
  - `maven-metadata-local.xml`

### 2.9 `kli dependency <subcommand>`

Manages runtime and test dependencies declared in `project.json`.

```
kli dependency list
kli dependency list --tree
kli dependency list --tree --format json
kli dependency status
kli dependency add <coordinate>
kli dependency remove <coordinate>
kli dependency upgrade [coordinate]
```

All mutating dependency commands (`add`, `remove`, `upgrade`) run `kli clean` automatically after writing `project.json`, because dependency changes invalidate the compilation cache. Use `--no-clean` to skip this.

#### 2.9.1 `kli dependency list`

Prints dependencies grouped by scope (`deps` and `testDeps`).

```
$ kli dependency list
Runtime dependencies:
  io.ktor:ktor-server-netty:3.0.0
  com.zaxxer:HikariCP:6.0.0

Test dependencies:
  org.jetbrains.kotlin:kotlin-test:2.0.0
```

Rules:
- If only one scope contains dependencies, print only that scope heading
- If no dependencies exist, print `(no dependencies)`

Options:
- `--scope runtime|test|all` (default `all`): restrict output to one scope
- `--tree`: resolve and print full transitive dependency tree
- `--format text|json` (default `text`): output human-readable text or structured JSON

Tree output example:

```
$ kli dependency list --tree --scope runtime
Runtime dependency tree:
- io.ktor:ktor-server-netty:3.1.2
+-- io.ktor:ktor-server-core:3.1.2
+-- org.slf4j:slf4j-api:2.0.17
```

Notes:
- Tree glyphs are light gray in text mode.
- Coordinate separators (`:`) are light gray in text mode.
- Top-level coordinates are prefixed with `- `.

JSON tree output example:

```json
{
  "scope": "runtime",
  "runtime": [
    {
      "coordinate": "io.ktor:ktor-server-netty:3.1.2",
      "children": [
        {
          "coordinate": "io.ktor:ktor-server-core:3.1.2",
          "children": []
        }
      ]
    }
  ],
  "test": []
}
```

#### 2.9.2 `kli dependency status`

Resolves configured dependencies and reports whether newer versions are available.

```
$ kli dependency status
Checking 5 dependencies against Maven Central...

Runtime dependencies:
  io.ktor:ktor-server-netty         3.0.0    -> 3.1.2   (latest)
  com.zaxxer:HikariCP               6.0.0    ✓ up to date
```

Output semantics:
- Green `✓` for up to date
- Yellow `->` for update available
- Red `✗` if the dependency cannot be resolved

Options:
- `--registry <url>`: override registry used for version checks
- `--offline`: do not query remote registries, use local cache only
- `--format json`: machine-readable output for CI/tooling
- `--show-all`: include all available versions

Offline/unknown latest example:

```
io.ktor:ktor-server-netty         3.0.0    ? (offline)
```

Exit codes:
- `0`: all dependencies up to date
- `2`: update available for at least one dependency or resolution failure

#### 2.9.3 `kli dependency add <coordinate>`

Adds a dependency to `project.json` and then cleans cache unless disabled.

```
$ kli dependency add io.ktor:ktor-server-netty:3.0.0
✓ Added io.ktor:ktor-server-netty:3.0.0 (runtime)
✓ Cache cleaned
```

Options:
- `--scope runtime|test` (default `runtime`)
- `--latest`: resolve and use latest version when version is omitted
- `--registry <url>`: override registry for `--latest`
- `--no-clean`: skip automatic clean

Rules:
- If version is missing and `--latest` is not provided, fail with guidance
- If exact coordinate already exists, print warning and no-op
- If `group:artifact` exists at a different version, fail and suggest `dependency upgrade`

#### 2.9.4 `kli dependency remove <coordinate>`

Removes dependency entries from `project.json` and then cleans cache unless disabled.

Coordinate matching:
- `group:artifact:version`: remove exact match
- `group:artifact`: remove any version of artifact
- `group`: remove all artifacts in that group

Options:
- `--scope runtime|test`: restrict removal scope
- `--yes`, `-y`: skip confirmation prompts
- `--no-clean`: skip automatic clean

If no matching dependency exists, command fails with an error.

#### 2.9.5 `kli dependency upgrade [coordinate] [version]`

Upgrades one dependency or all dependencies.

```
$ kli dependency upgrade io.ktor:ktor-server-netty
? Resolving latest version from Maven Central...
✓ Upgraded io.ktor:ktor-server-netty from 3.0.0 -> 3.1.2
✓ Cache cleaned
```

Modes:
- `kli dependency upgrade`: upgrade all dependencies to latest compatible resolved version
- `kli dependency upgrade group:artifact`: upgrade one dependency to latest
- `kli dependency upgrade group:artifact:version`: set one dependency to explicit version

Major version bumps:
- Show warning for `N.x -> (N+1).x`
- Require confirmation unless `--yes` is supplied

Options:
- `--scope runtime|test`: restrict upgrade scope
- `--yes`, `-y`: skip major-bump confirmation
- `--dry-run`: print planned changes only
- `--registry <url>`: override registry
- `--offline`: upgrade only to versions available in local cache
- `--no-clean`: skip automatic clean

Exit codes:
- `0`: all targeted dependencies already at desired versions
- `2`: one or more upgrades failed

### 2.10 `kli publish [--registry <url>]`

*(Future / stretch goal)*

Publishes the packaged artifact to a Maven-compatible registry.

```
kli publish --registry https://repo.example.com/releases
```

**Behaviour:**
1. Run `package` (or verify the package exists)
2. Deploy the fat JAR and `pom.xml` to the specified registry
3. Default registry from `project.json` → `publish.registry` (or `https://repo.maven.apache.org/maven2` if unset)

### 2.11 `kli build --output <path>`

*(Future / stretch goal)*

Builds a fat JAR with all discovered mains and a dispatcher entrypoint. The selected main is provided at runtime.

```
kli build --output ./dist/app.jar
java -jar ./dist/app.jar tools.CLI
```

### 2.12 Exit Codes

| Command | Exit code 0 | Exit code 1 | Exit code 2 |
|---|---|---|---|
| `kli run <qualified-name>` | Program exited successfully | Build/runtime error | CLI usage error (invalid args, unknown command) |
| `kli test [path]` | All tests passed | Test failures or test runtime error | CLI usage error |
| `kli clean` | Cache removed or already clean | Failed to remove cache | CLI usage error |
| `kli clean-all` | Global cache removed or already clean | Failed to remove global cache | CLI usage error |
| `kli init [name]` | Project created | Scaffolding failed | CLI usage error |
| `kli project-lint` | Config valid | Config invalid | CLI usage error |
| `kli refresh` | Dependencies refreshed and project rebuilt | Refresh/build failed | CLI usage error |
| `kli dependency list` | Dependencies listed | Failed to read config | CLI usage error |
| `kli dependency status` | All dependencies up to date | Status check failed | At least one update available, or at least one dependency resolution failed |
| `kli dependency add <coordinate>` | Dependency added (or already present no-op) | Failed to update config | CLI usage error |
| `kli dependency remove <coordinate>` | Dependency removed | Failed to update config | CLI usage error |
| `kli dependency upgrade [coordinate]` | All targeted dependencies at desired version | Failed to update config | At least one targeted upgrade failed |
| `kli build --output <path>` | JAR built successfully | Build failed | CLI usage error |
| `kli package [--output <path>]` | JAR built and installed to `~/.kli/m2/` | Build/install failed | CLI usage error |
| `--silent` flag on run/test/package | Hides light-gray dependency/compile progress lines | n/a | n/a |
| `kli publish [--registry <url>]` | Artifact published | Publish failed | CLI usage error |

### 2.13 Global Options

| Option | Behaviour |
|---|---|
| `--version` | Display tool version and exit with code 0 |
| `--help` or `-h` | Display help for the command and exit with code 0 |

---

## 3. Project Structure (Convention)

```
my-project/
├── project.json              # required
├── tools/                    # CLI entrypoints and scripts
│   ├── Server.kt             # fun main()
│   ├── ServerTest.kt         # tests for Server.kt (co-located)
│   └── CLI.kt                # fun main()
├── services/                 # application/service layer
│   ├── Worker.kt             # fun main() or shared service code
│   └── WorkerTest.kt         # tests for Worker.kt (co-located)
└── lib/                      # reusable/supporting library code
    ├── Database.kt           # shared code, no main
    └── DatabaseTest.kt       # tests for Database.kt (co-located)
```

**Rules:**
- Any `.kt` file under the project root is a potential source file
- A source file is considered a "main entrypoint" if it contains `fun main()`
- A source file is considered a "test file" if its name ends `Test.kt`
- Tests should be co-located with the files they test (no dedicated `tests/` directory required)
- Top-level folders (for example `tools/`, `services/`, `lib/`) should reflect project intent and may vary by project type
- All source files share the same classpath (resolved from `project.json`)
- No `src/` convention — you organise how you want

---

## 4. Configuration (`project.json`)

Minimal, validated schema. Example:

```json
{
  "name": "my-api",
  "version": "0.2.0",
  "deps": [
    "io.ktor:ktor-server-netty:3.0.0",
    "com.zaxxer:HikariCP:6.0.0",
    "ch.qos.logback:logback-classic:1.5.0"
  ],
  "testDeps": [
    "io.mockk:mockk:1.13.0"
  ],
  "target": "21",
  "resources": ["config/**", "templates/**"],
  "repos": [
    "https://repo.maven.apache.org/maven2"
  ]
}
```

### Schema

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | `string` | No | Directory name | Project name (used in package, publish) |
| `version` | `string` | No | `"0.1.0"` | Project version (used in package, publish) |
| `deps` | `string[]` | No | `[]` | Maven coordinates (`group:artifact:version`) |
| `testDeps` | `string[]` | No | `[]` | Test-only Maven coordinates |
| `target` | `string` | No | `"21"` | Target JVM bytecode version |
| `sources` | `string[]` | No | `["."]` | Directories to scan for `.kt` files, `*Test.kt`, and resource globs |
| `resources` | `string[]` | No | `[]` | Glob patterns for resource files (copied to cache and classpath; bundled in fat JAR) |
| `jvmArgs` | `string[]` | No | `[]` | JVM arguments passed at runtime |
| `repos` | `string[]` | No | `["https://repo.maven.apache.org/maven2"]` | Additional Maven repositories |
| `publish.registry` | `string` | No | `"https://repo.maven.apache.org/maven2"` | Registry URL for publishing |
| `publish.repoId` | `string` | No | — | Repository ID for credentials lookup in `~/.kli/m2/settings.xml` |

### Resolution order

1. Built-in defaults
2. `project.json` values merge over defaults (deep merge)
3. CLI flags override `project.json` values

---

## 5. Technical Architecture

### 5.1 Cache Layout

```
~/.kli/
├── m2/                              # Maven dependency cache (shared across projects)
│   ├── org.apache...
│   ├── io.ktor...
│   ├── io/kli/local/                # locally packaged artifacts (kli package)
│   │   └── <artifact>/<version>/
│   │       ├── <artifact>-<version>.jar
│   │       ├── <artifact>-<version>.pom
│   │       └── maven-metadata-local.xml
│   └── ...
├── cache/
│   ├── <project-root-hash>/         # Per-project cache
│   │   ├── classes/                 # Compiled .class files
│   │   ├── resources/               # Resolved resource files (copied from globs)
│   │   ├── gen/                     # Synthetic test runner files
│   │   └── manifest.json            # Hash tracking for incremental compilation
│   └── ...
```

**Design decisions:**
- **Global Maven cache** (`~/.kli/m2/`) — shared across all projects, avoids re-downloading
- **Per-project class cache** (`~/.kli/cache/<hash>/`) — isolated, allows different Kotlin versions per project
- **Project root is hashed** via SHA-256 of the full absolute path — produces a unique but deterministic cache key; avoids collisions from different projects with the same name
- **No `.kotlin/` in the project directory** — user's source tree is pristine, only contains source code

### 5.2 Dependency resolution

- Use **Maven Resolver API** (`org.apache.maven:maven-resolver`) to resolve Maven coordinates
- Cache resolved artifacts in `~/.kli/m2/`
- Resolved classpath is stored in the per-project manifest for reuse
- Test dependencies (`testDeps`) are resolved separately and only added to the classpath during `test` commands

### 5.3 Compilation

- Use **Kotlin compiler embeddable** (`org.jetbrains.kotlin:kotlin-compiler-embeddable`)
- Invoke programmatically via `K2JVMCompiler` or `KotlinCompilation` API
- Output class files to `~/.kli/cache/<hash>/classes/`
- Track source file hashes (SHA-256) for incremental compilation
- Only recompile files whose hash changed or whose dependencies changed

### 5.4 Incremental compilation

```json
// ~/.kli/cache/<hash>/manifest.json
{
  "tools/Server.kt": {
    "hash": "sha256:abc123...",
    "output": "tools/ServerKt.class",
    "sourceDeps": ["lib/Database.kt", "lib/Config.kt"],
    "transitiveSourceDeps": ["lib/Database.kt", "lib/Config.kt", "lib/Env.kt"],
    "classpathFingerprint": "sha256:runtime-deps-hash...",
    "testClasspathFingerprint": "sha256:test-deps-hash..."
  },
  "lib/Database.kt": {
    "hash": "sha256:def456...",
    "output": "lib/Database.class",
    "sourceDeps": [],
    "transitiveSourceDeps": [],
    "classpathFingerprint": "sha256:runtime-deps-hash..."
  }
}
```

### 5.5 Cache invalidation rules

| Event | Action |
|---|---|
| Source file edited | Recompile that file (hash change detected in manifest) |
| `deps` or `testDeps` changed in `project.json` | Clear per-project cache, re-resolve, recompile all |
| Compiler version changed in tool binary | Clear ALL project caches (or at minimum, the affected project) |
| `kli refresh` | Re-resolve deps (especially for SNAPSHOTs), recompile all |
| SNAPSHOT dependency | Not automatically refreshed — user runs `kli refresh` explicitly |

### 5.6 Resource handling

Resources are directory glob patterns specified in `project.json` → `resources`.

```
"resources": ["config/**", "templates/**", "public/**"]
```

**Behaviour:**
1. At compile time, resolve each glob pattern against each configured source root
2. Copy matched files into `~/.kli/cache/<hash>/resources/` preserving relative paths
3. Add the resources directory to the classpath so `ClassLoader.getResource()` works
4. At fat JAR build time, bundle resources into the JAR root

### 5.7 Test runner

**Discovery phase:**
- Glob `**/*Test.kt` relative to configured source roots
- Exclude any paths inside cache directories
- Tests are authored with `kotlin.test` annotations

**Generation phase:**
Create a synthetic file `~/.kli/cache/<hash>/gen/TestRunner.kt`:

```kotlin
package __test_runner__

import kotlin.test.*
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.engine.discovery.DiscoverySelectors

fun main() {
  // Discover and execute Kotlin tests through JUnit 5 launcher APIs.
    // Exit with non-zero code if any test fails
}
```

**Execution phase:**
- Compile the generated runner together with all test files
- Run the generated `main()`
- Print results in a human-readable format
- Exit code 0 = all pass, non-zero = failures

### 5.8 `package` command internals *(future)*

- Name defaults to directory name if not in `project.json`
- Version defaults to `"0.1.0"` if not in `project.json`
- Output path defaults to `./dist/<name>-<version>.jar`
- Fat JAR includes all compiled classes + resolved dependency contents + resources
- Package installs the built artifact into `~/.kli/m2/`

### 5.9 `clean` command internals

```
Step 1: Walk up from cwd to find project.json (project root)
Step 2: Compute SHA-256 of full absolute project root path
Step 3: Delete ~/.kli/cache/<hash>/
```

**What gets deleted:**
- All compiled `.class` files
- Hash manifest
- Generated test runner files
- Copied resource files

**What stays:**
- `project.json`
- All user source files (`.kt`, `*Test.kt`)
- `~/.kli/m2/` (global Maven cache — shared with other projects)

### 5.10 Fat JAR assembly *(future)*

- Collect all compiled class files from cache
- Extract dependency JARs without relocation
- Merge `META-INF/services/` files
- Generate `META-INF/MANIFEST.MF` with dispatcher `Main-Class`
- Include all resolved resources at JAR root
- Zip everything into the output file

### 5.11 `dependency` command internals

For `list`, `add`, `remove`, and `upgrade`, kli operates directly on `project.json`:

1. Parse current config into a `ProjectConfig`
2. Modify `deps` and/or `testDeps`
3. Serialize config back to JSON with stable formatting and field order

For `status` and `upgrade`, latest-version resolution queries Maven metadata from configured repositories (default Maven Central):

```
GET https://repo.maven.apache.org/maven2/io/ktor/ktor-server-netty/maven-metadata.xml
```

In offline mode, remote metadata lookups are skipped and only local cache metadata is considered.

---

## 6. Implementation Plan

### Phase 1 — Core (weekend project)

- [x] CLI argument parsing (using `clikt`)
- [x] `project.json` reading + validation
- [x] Project root walking (find `project.json` in parent dirs)
- [x] Maven dependency resolution (cached to `~/.kli/m2/`)
- [x] Kotlin compilation (embeddable compiler)
- [x] `run` command
- [x] Source file hashing + incremental recompile
- [x] `clean` command
- [x] `clean-all` command
- [x] `project-lint` command
- [x] `package` command (build + install to local `~/.kli/m2/`)

### Phase 2 — Tests & Resources

- [x] `test` command
- [x] Test file discovery (`**/*Test.kt`)
- [x] Synthetic test runner generation
- [x] Test result reporting
- [x] Resource glob resolution and copying
- [x] `testDeps` support (separate test classpath)

### Phase 3 — Quality of life

- [x] `init` command (scaffolding)
- [x] `refresh` command (SNAPSHOT re-resolution)
- [x] `dependency` command family (`list`, `status`, `add`, `remove`, `upgrade`)
- [x] Better error messages with suggestions
- [x] `--version` flag
- [x] Shell completion (optional)

### Phase 4 — Package & Publish

- [x] `build` command (fat JAR with dispatcher)
- [x] `publish` command (deploy to Maven registry)
- [x] Service file merging

---

## 7. Dependencies (tool itself)

The CLI tool itself should be minimal. Recommended dependencies:

| Dependency | Purpose |
|---|---|
| `org.jetbrains.kotlin:kotlin-compiler-embeddable` | Compile user source files |
| `org.apache.maven:maven-resolver-provider` | Resolve Maven deps |
| `org.apache.maven.resolver:maven-resolver-connector-basic` | Download artifacts |
| `org.apache.maven.resolver:maven-resolver-transport-http` | HTTP transport |
| `com.github.ajalt.clikt:clikt` | CLI argument parsing |
| `org.jetbrains.kotlin:kotlin-test` | Kotlin test annotations/assertions |
| `org.jetbrains.kotlin:kotlin-test-junit5` | Bridge for Kotlin tests on JUnit 5 |
| `org.junit.platform:junit-platform-launcher` | Programmatic test execution |
| `org.junit.jupiter:junit-jupiter-engine` | JUnit 5 test engine |
| `com.charleskorn.kaml:kaml` | YAML/JSON parsing if needed |

The tool itself should be distributed as a single executable — either a fat JAR or a native image via GraalVM.

---

## 8. Comparison to existing tools

| Feature | kli (this) | Gradle | Maven | JBang | KScript |
|---|---|---|---|---|---|
| Config file | `project.json` | `build.gradle.kts` | `pom.xml` | None (file annotations) | None (file annotations) |
| Config style | JSON (minimal) | DSL (full language) | XML (verbose) | Javadoc annotations | Kotlin annotations |
| Run command | `kli run X` | `gradle run` | `mvn exec:java` | `jbang X.kt` | `kscript X.kt` |
| Multi-main | ✅ (any dir) | ❌ (one per module) | ❌ (one per module) | ❌ (file-scoped) | ❌ (file-scoped) |
| Test discovery | `**/*Test.kt` | Convention (src/test/) | Convention (src/test/) | ❌ | ❌ |
| Clean command | ✅ (built-in) | `gradle clean` | `mvn clean` | ❌ | ❌ |
| Clean-all command | ✅ (built-in) | ❌ | ❌ | ❌ | ❌ |
| Config lint | ✅ (`project-lint`) | ❌ (error at runtime) | ❌ (error at runtime) | ❌ | ❌ |
| Auto-dependency | ✅ (Maven resolver) | ✅ (Gradle) | ✅ (Maven) | ✅ (Maven resolver) | ✅ (Maven resolver) |
| Test deps | ✅ (`testDeps`) | ✅ | ✅ | ❌ | ❌ |
| Resources | ✅ (globs) | ✅ | ✅ | ❌ | ❌ |
| Fat JAR | ✅ (planned) | ✅ (Shadow plugin) | ✅ (Shade plugin) | ❌ | ❌ |
| Package & Publish | ✅ (planned) | ✅ (`maven-publish`) | ✅ (`deploy`) | ❌ | ❌ |
| Plugin system | ❌ (out of scope) | ✅ | ✅ | ❌ | ❌ |
| IDE integration | ❌ (terminal-first) | ✅ | ✅ | ❌ | ❌ |
| Cache location | `~/.kli/` (global) | Project-local | `~/.m2/` (global) | `~/.jbang/` (global) | `~/.kscript/` (global) |
| Project dir clean | ✅ (always) | ❌ (build/ dir) | ❌ (target/ dir) | ✅ (N/A) | ✅ (N/A) |
| Shebang support | ✅ (Kotlin lexer strips `#!`) | ❌ | ❌ | ✅ | ✅ |

---

## 9. Success Criteria

The tool is successful when:

```bash
# Create a project
mkdir my-api && cd my-api
kli init

# Add a dependency (edit project.json)

# Or use dependency commands
kli dependency add io.ktor:ktor-server-netty:3.0.0
kli dependency status
kli dependency upgrade --dry-run
kli dependency remove io.ktor:ktor-server-netty

# Run the server
kli run tools.Server

# Run tests
kli test

# Clean up cached artifacts
kli clean

# Clean everything globally
kli clean-all

# Validate project.json
kli project-lint

# Package for distribution
kli package --output ./dist/my-api.jar

# Cold start time depends on network and uncached dependency downloads.
# Warm run/test/build on an unchanged project should complete near-instantly.
```

---

## 10. Decisions Log

| Question | Decision |
|---|---|
| **Tool name** | `kli` — pun on "command-line", short, unique |
| **Distribution** | Single JAR (fat JAR of the tool itself). GraalVM native binary considered but deferred — embedding the Kotlin compiler in a native image is complex |
| **Maven cache location** | `~/.kli/m2/` — shared across all projects |
| **Project cache location** | `~/.kli/cache/<project-root-hash>/` — per-project, deterministic |
| **Project root hash** | SHA-256 of full absolute path. `clean-all` clears all cache entries |
| **Kotlin version** | Bundled with the tool binary (static dependency for v1) |
| **Config strictness** | Loose (unknown keys ignored) for forward-compatibility. `project-lint` command catches typos strictly |
| **Config format** | Strict JSON. JSON5 nice-to-have, deferred |
| **Test framework** | `kotlin.test` authoring with generated JUnit 5 runner execution |
| **Error handling** | Clean human-readable messages by default. `--verbose` flag enables full stack traces |
| **Minimum JVM target** | `21` (current LTS). Overridable via `project.json` → `target` |
| **Walk up for project.json** | Yes — like git, like cargo. Run from any subdirectory |
| **Test-specific deps** | Yes — `testDeps` field keeps test-only dependencies off the runtime classpath |
| **Resources** | Glob patterns in `resources` field. Copied to cache, on classpath, bundled in fat JAR |
| **Cache invalidation** | Compiler version change → clear cache. Dependency version change → clear cache. SNAPSHOT → `refresh` command |
| **Package semantics** | `package` performs build + install to local `~/.kli/m2/` |
| **Plugin system** | Out of scope for v1 |
| **Project name** | Defaults to directory name if not in `project.json` |
| **Project version** | Defaults to `"0.1.0"` if not in `project.json` |
| **Shebang** | Supported with `#!/usr/bin/env kli run` |
