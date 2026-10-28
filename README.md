# Enterprise Base

One repository with three independent projects. They talk to each other only over REST/JSON over HTTPS. None of them imports code from another, and each has its own build.

| Folder | What it is |
|---|---|
| `enterprise-db/` | SQL Server T-SQL: tables, stored procedures, functions, seed data, Flyway-versioned migrations |
| `enterprise-api/` | Java / Spring Boot (Maven) REST API that calls the stored procedures *(not scaffolded yet)* |
| `enterprise-ui/` | React + TypeScript (Vite) frontend *(not scaffolded yet)* |

## Getting started

```bash
git clone https://github.com/Gokulavasan-A/enterprise-base.git
cd enterprise-base
```

Run git commands from the repository root. Run build and run commands from inside each project folder.

Set the tiers up in order:
1. **enterprise-db:** create the database and apply the migrations (see [enterprise-db/README.md](enterprise-db/README.md)).
2. **enterprise-api**
3. **enterprise-ui**

The coding rules and architecture for all three projects are in [CLAUDE.md](CLAUDE.md).
