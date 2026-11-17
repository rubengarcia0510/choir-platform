# Development and Release Workflow

## Backend branch strategy

The backend does not use a permanent integration branch.

- `main` is the source branch for backend feature work.
- Every feature or fix branch must be created from an updated `origin/main`.
- Feature pull requests target `main`.
- Never commit feature work directly to `main`.
- Use a dedicated branch for each ticket or isolated change.
- Do not reuse branches from previously completed work.

## Coordinated frontend and backend releases

CHOIR frontend and backend are released as one coordinated product version.

Use Semantic Versioning (`MAJOR.MINOR.PATCH`) without a `v` prefix.

- MAJOR: incompatible changes.
- MINOR: backward-compatible functionality.
- PATCH: backward-compatible fixes.

The same version number must be used by both repositories for a coordinated release.

### Release branches

- Backend: create `release/X.Y.Z` from the updated backend `main`.
- Frontend: create `release/X.Y.Z` from the updated frontend `124-integradora`.
- Do not create or assume a permanent backend integration branch.
- Do not release the frontend directly from `main` when the integration branch contains the changes intended for that release.

### Version updates

Only update versions when preparing an actual release, not for every feature branch.

For a release:

- Frontend: update `package.json` and `package-lock.json`.
- Backend: update the `version` declaration in `build.gradle`.
- Use the exact same version in both repositories.

### Release validation and publication

1. Confirm that the frontend integration branch contains the intended changes and is synchronized with the latest frontend `main`.
2. Confirm that backend `main` contains the intended backend changes.
3. Create the corresponding release branches from their respective source branches.
4. Update both repository versions to the same SemVer value.
5. Run the relevant tests and production builds in both repositories.
6. Review both release diffs and confirm that the versions match.
7. Open separate pull requests:
   - Backend release branch into backend `main`.
   - Frontend release branch into frontend `main`.
8. Merge and publish only after both release candidates have been reviewed and validated.
9. Create matching Git tags and GitHub Releases in both repositories, using the same version and no `v` prefix.
10. Synchronize frontend `main` back into `124-integradora` through a pull request after the release is integrated.

Never claim a coordinated release is complete while only one repository has been released.
