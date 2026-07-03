# AGENTS.md

## Purpose
Repository-level working agreement for implementing kli (Kotlin CLI tool) from the specification in docs/kotlin-cli-spec.md.

## Current Project Decisions
- Project is implemented as a Kotlin/Gradle project.
- Build uses Kotlin DSL: build.gradle.kts + settings.gradle.kts.
- Runtime/toolchain target is JDK 21.
- Gradle wrapper is the source of truth for local/CI builds.
- Source of truth for product behavior remains docs/kotlin-cli-spec.md.

## Development Workflow
- Implement in small vertical slices that map to the implementation phases in the spec.
- Keep changes focused and atomic; avoid unrelated refactors.
- Use ./gradlew for all build and test execution.

## Testing Policy (Mandatory)
- No feature is complete without automated unit test coverage.
- Every feature change must include at least one new or updated unit test that proves behavior.
- Bug fixes must include a regression test that would fail before the fix.
- If a change cannot be unit tested directly, add a narrow integration test and explain why unit tests are insufficient.
- Tests are part of the definition of done, not follow-up work.

## Definition Of Done
A change is complete only when all conditions below are true:
1. Implementation matches behavior in docs/kotlin-cli-spec.md.
2. Automated test(s) for the change are present and passing.
3. Full project verification passes via ./gradlew test build.
4. User-facing behavior/error messages are updated in docs when applicable.

## Review Checklist
- Is there unit test coverage for each new feature path?
- Is there regression coverage for each bug fix?
- Are edge/error paths tested where behavior changed?
- Do tests run with ./gradlew test and pass in CI/local?
- Are spec/docs updates included when behavior changed?

## Notes
- Keep Gradle wrapper files committed.
- Do not commit generated build outputs.
