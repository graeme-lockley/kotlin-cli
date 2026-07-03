# kli

A minimal Kotlin CLI for running, testing, packaging, and publishing Kotlin projects with simple conventions.

## What kli Does

- Runs Kotlin entrypoints by qualified source name.
- Discovers and runs Kotlin tests (`*Test.kt`).
- Manages runtime and test dependencies in `project.json`.
- Builds and packages fat JAR artifacts.
- Keeps build outputs out of your project directory by using `~/.kli` caches.

## Quick Start

### Requirements

- JDK 21
- macOS, Linux, or Windows

### Build From Source

```bash
./gradlew test build
```

Run the CLI through Gradle:

```bash
./gradlew run --args="--help"
```

Or use generated scripts after build:

- `build/scripts/kotlin-cli` (Unix)
- `build/scripts/kotlin-cli.bat` (Windows)

## Core Commands

```bash
kli init my-project
kli run tools.Server
kli test
kli dependency list
kli package
```

Common options:

- `--show-compiler-logging` for compiler diagnostics (`run`, `test`, `build`, `package`)
- `--silent` to hide dependency and compile progress lines
- `--verbose` on command errors to print stack traces

## Minimal `project.json`

```json
{
  "name": "my-project",
  "version": "0.1.0",
  "deps": [],
  "testDeps": [],
  "target": "21"
}
```

Important fields:

- `deps`: runtime dependencies (`group:artifact:version`)
- `testDeps`: test-only dependencies
- `sources`: directories to scan (default `["."]`)
- `resources`: resource globs copied to runtime and package classpath
- `repos`: Maven repositories (default Maven Central)
- `jvmArgs`: JVM args forwarded at runtime

## Project Layout Convention

kli does not require a `src/` layout. Kotlin files can live anywhere under configured source roots.

```text
my-project/
├── project.json
├── tools/
│   ├── CLI.kt
│   └── CLITest.kt
├── services/
│   ├── Worker.kt
│   └── WorkerTest.kt
└── lib/
    ├── Database.kt
    └── DatabaseTest.kt
```

Conventions:

- Entrypoints are top-level `fun main(...)`.
- Qualified name `tools.CLI` maps to `tools/CLI.kt`.
- Tests are files ending in `*Test.kt`, ideally co-located with the code they validate.

## Dependency Management

```bash
kli dependency list
kli dependency status
kli dependency add io.ktor:ktor-server-netty:3.1.2
kli dependency add --scope test io.mockk:mockk:1.13.12
kli dependency remove io.ktor:ktor-server-netty
kli dependency upgrade
```

Dependency mutations (`add`, `remove`, `upgrade`) clean the project cache by default. Use `--no-clean` to skip.

## Cache Locations

- Global Maven cache: `~/.kli/m2/`
- Project cache: `~/.kli/cache/<project-hash>/`

Use:

- `kli clean` to clear the current project cache
- `kli clean-all` to clear all project caches

## More Documentation

- Product and behavior reference: `docs/kotlin-cli-spec.md`
- Agent usage guide: `docs/agent-guide.md`
