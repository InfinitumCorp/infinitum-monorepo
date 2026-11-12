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

GitHub Actions only reads workflows from the root `.github/workflows/`, so the workflows inside each project folder do not run here yet. They still run in each original repo.
