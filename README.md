# infinitum-monorepo

Five InfinitumCorp projects in one repository, each in its own folder with its full commit history.

| Folder | Source repo | Merged from |
|---|---|---|
| `Conjure/` | InfinitumCorp/Conjure | `main` |
| `infinitum-nexus/` | InfinitumCorp/infinitum-nexus | `main` |
| `architect/` | InfinitumCorp/architect | `main` |
| `infinitum-revenue/` | InfinitumCorp/infinitum-revenue | `main` |
| `throne-os/` | InfinitumCorp/throne-os | `main` |

The original repos still exist and are unchanged.

## Pulling newer changes from an original repo

```bash
git subtree pull --prefix=<folder> https://github.com/InfinitumCorp/<repo>.git main
```

## CI

Each project has its own workflow in the root `.github/workflows/` (for example `throne-os.yml`). A workflow runs only when files in its project folder change, and a newer push cancels the older run.

The workflows inside each project folder are not used by the monorepo; GitHub only reads the root folder. Deploy, scheduled and macOS workflows were not carried over, so deploys still run from each original repo.

To move Linux jobs to the self-hosted runners, set the repository variable `CI_RUNNER=infinitum-ci`.
