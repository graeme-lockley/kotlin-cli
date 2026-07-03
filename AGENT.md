# AGENT.md

## Purpose
This repository currently contains the specification for kli, a Kotlin CLI tool.
The primary source of truth is docs/kotlin-cli-spec.md.

## Scope
- Prefer documentation-first changes.
- Keep the spec coherent across commands, architecture, and decisions.
- Avoid adding implementation code unless explicitly requested.

## Writing Standards
- Be precise, actionable, and concise.
- Keep terminology consistent (command names, paths, cache locations, file patterns).
- Use examples that match documented behavior.
- Update all affected sections when changing a feature decision.

## Review Checklist
- Command behavior matches schema and architecture sections.
- Path conventions are consistent throughout the document.
- Test naming and discovery patterns are consistent in all sections.
- Future-scope items are clearly marked and not mixed with v1 guarantees.
- No contradictory defaults between examples and schema tables.

## Change Management
- Prefer small, focused commits with Conventional Commit messages.
- Include rationale in commit body when changing major decisions.
- If a change is ambiguous, ask concise clarifying questions before editing.
