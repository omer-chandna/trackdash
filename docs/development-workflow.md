# Development workflow

1. Inspect the working tree, relevant source, tests, and roadmap.
2. Scope one independently reviewable feature and write acceptance criteria.
3. Branch from an approved integration point. Preserve user changes.
4. Implement in small increments, preferably test-first for deterministic logic.
5. Run relevant unit tests and compilation continuously; lint and assemble at completion.
6. Review the diff and selectively stage related paths.
7. Commit with a meaningful Conventional Commit message (roughly 100–300 lines
   where practical; generated wrapper files and cohesive scaffolding may exceed this).
8. Write a PR-style summary with actual validation and known limitations.
9. Await explicit integration authorization. Do not merge automatically.

Branches use feature/, fix/, test/, refactor/, or docs/ according to purpose.
Dependent work starts only after its base is approved for integration. No force
pushes, destructive resets, history rewriting, or deletion of user branches.
Publishing a draft PR also requires authorization. Until then keep local commits
and a review summary. Main remains the stable integration branch.
