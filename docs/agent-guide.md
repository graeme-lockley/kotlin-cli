# Agent Guide: Using kli on Kotlin Projects

This guide is for agents that use kli to work on Kotlin projects.

It explains how to run code, run tests, manage dependencies, and package artifacts using the CLI, plus the project layout idioms kli expects.

## Agent Prompt Template

Use this template when handing work to another agent:

```text
Goal:
- <describe the outcome>

Project context:
- Root contains project.json: <yes/no>
- Source roots (project.json:sources): <value or default .>
- Target file(s) or module(s): <paths>

Required kli workflow:
1. Run kli project-lint before edits if config validity is unclear.
2. Make the smallest change that satisfies the goal.
3. If dependencies must change, use kli dependency add/remove/upgrade.
4. Run kli test after behavior changes.
5. If an artifact is required, run kli build or kli package.

Constraints:
- Preserve existing project structure and naming conventions.
- Keep tests co-located and named *Test.kt.
- Report commands run, key output, and final exit codes.

Deliverables:
- Files changed
- Tests run and results
- Any follow-up risks or TODOs
```

## What kli Is

kli is a zero-boilerplate Kotlin CLI.

Use it when you want to:

- run a Kotlin entrypoint quickly
- run kotlin.test tests without full Gradle or Maven setup
- manage dependencies in one json file
- build a runnable fat jar

## Core Mental Model

- Project config lives in project.json at the project root.
- Source files can live anywhere under configured sources (default is current directory).
- Entrypoints are top-level main functions in Kotlin files.
- Test files are discovered by filename suffix Test.kt.
- Build artifacts and cache stay outside the project tree in ~/.kli.

## Project Structure Idioms

kli is convention-based, not src-layout-based.

Typical structure:

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

Idioms:

- Keep tests close to the code they verify.
- Use clear folder intent names like tools, services, lib.
- Keep entrypoints as top-level main functions.

## Qualified Name Mapping

The run target uses a qualified source name.

Example mapping:

- tools.Server -> tools/Server.kt
- scripts.Migrate -> scripts/Migrate.kt

Rule:

- Take the relative path from a configured source root.
- Replace path separators with dots.
- Remove the .kt extension.

## project.json Reference

Minimal example:

```json
{
  "name": "my-project",
  "version": "0.1.0",
  "deps": [],
  "testDeps": [],
  "target": "21"
}
```

Useful fields:

- name: project name
- version: artifact version
- deps: runtime dependencies, format group:artifact:version
- testDeps: test-only dependencies
- sources: directories to scan for Kotlin files
- resources: glob patterns to include on runtime or package classpath
- jvmArgs: jvm flags for runtime
- repos: Maven repository urls
- publish.registry: publish target registry
- publish.repoId: credentials id for Maven settings lookup

## Command Cookbook

### Initialize

```bash
kli init my-project
```

### Run Code

```bash
kli run tools.Server
kli run tools.Server -- --port 8080
kli run --verbose tools.Server
```

### Run Tests

```bash
kli test
kli test tools/
kli test tools/SomeServiceTest.kt
```

### Validate Config

```bash
kli project-lint
```

### Dependency Operations

```bash
kli dependency list
kli dependency list --tree
kli dependency list --tree --scope runtime
kli dependency list --tree --format json
kli dependency list --format json
kli dependency status
kli dependency add io.ktor:ktor-server-netty:3.1.2
kli dependency add --scope test io.mockk:mockk:1.13.12
kli dependency remove io.ktor:ktor-server-netty
kli dependency upgrade
```

### Build and Package

```bash
kli build --output ./dist/app.jar
kli package --output ./dist/app.jar
```

### Publish

```bash
kli publish --registry https://repo.example.com/releases
```

### Cache Maintenance

```bash
kli clean
kli clean-all
kli refresh
```

## Exit Code Contract

- 0: success
- 1: command or runtime failure
- 2: CLI usage error

## Quick Reference

```bash
kli --help
kli --version
kli completion zsh
kli completion bash
```
