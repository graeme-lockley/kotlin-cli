---
name: commit-changes
description: 'Stage and commit repository changes using Conventional Commits. Use for creating clean commit history with validated type, optional scope, and concise subject line.'
argument-hint: 'What changed and why'
user-invocable: true
---

# Commit Changes (Conventional Commits)

Create a commit that follows https://www.conventionalcommits.org/en/v1.0.0/.

## When To Use
- You need to commit working tree changes.
- You want consistent commit messages and clean history.
- You are preparing work before opening a PR or pushing.

## Procedure
1. Confirm this is a Git repository.
- Run `git rev-parse --is-inside-work-tree`.
- If not a repo, stop and ask the user to initialize first (or invoke `/setup-repo`).

2. Review pending changes.
- Run `git status --short`.
- If there are no changes, report that there is nothing to commit and stop.

3. Build a Conventional Commit message.
- Use format: `<type>(<scope>): <description>` or `<type>: <description>`.
- Allowed `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- `description` requirements:
  - imperative mood
  - lower-case start preferred
  - no trailing period
  - <= 72 chars
- Ask concise clarifying questions if `type`, `scope`, or description is ambiguous.

4. Stage and commit.
- Default behavior: `git add -A` then `git commit -m "<message>"`.
- If user asks for partial commit, stage only requested files.

5. Report result.
- Provide commit hash, message, and file summary from `git show --stat --oneline -1`.

## Safety Rules
- Never rewrite history unless the user explicitly asks.
- Never run destructive Git commands (`reset --hard`, checkout discard patterns, etc.) without explicit approval.
- If commit fails, surface the error and suggest the minimal fix.
