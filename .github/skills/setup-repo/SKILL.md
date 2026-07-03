---
name: setup-repo
description: 'Initialize repository setup workflow: ensure git is initialized, create AGENT.md when missing, commit all files with Conventional Commits, and optionally configure/push GitHub remote.'
argument-hint: 'Repository setup request'
user-invocable: true
---

# Setup Repo

Prepare a project repository for first use and first push.

## When To Use
- Starting a new repository in a workspace.
- Standardizing initial repository setup.
- Creating an initial commit and optionally publishing to GitHub.

## Required Workflow
1. Ensure Git repository exists.
- Run `git rev-parse --is-inside-work-tree`.
- If not a repo, run `git init` in the project root.

2. Ensure commit skill exists and use it for committing.
- Confirm `.github/skills/commit-changes/SKILL.md` exists.
- If missing, create it with Conventional Commit rules.
- Use `/commit-changes` for all commits in this setup flow.

3. Ensure AGENT.md exists.
- Check for `AGENT.md` at repository root.
- If missing, create a reasonable best-practice `AGENT.md` based on repository contents.
- Ask concise clarifying questions before creating AGENT.md when project conventions are unclear.
- For a docs-first repository, include:
  - purpose and scope
  - document quality expectations
  - writing style and tone
  - review checklist
  - change management guidance

4. Stage and commit all files.
- Add all files intended for repository baseline.
- Invoke `/commit-changes` to create a Conventional Commit message.
- For initial baseline, prefer `chore(repo): initialize repository` unless user specifies a different intent.

5. Handle remote and GitHub push.
- Check for remotes with `git remote -v`.
- If no remote exists, ask whether to publish to GitHub.
- If yes, ask whether repo should be `public` or `private`.
- After confirmation, create/configure remote and push current branch.
- If GitHub CLI is unavailable or auth fails, provide exact next command and stop.

## Questions To Ask
- If commit intent is unclear:
  - "What is the primary intent for this commit: docs, chore, feat, fix, or refactor?"
- If AGENT.md is unclear:
  - "Should AGENT.md focus only on documentation workflows, or include future code workflows too?"
- If no remote exists:
  - "Would you like me to publish this repository to GitHub now?"
  - "Should the repository be public or private?"

## Output Requirements
- Report each completed step.
- For commits, include hash and final commit message.
- For remote setup, include remote URL and pushed branch.
