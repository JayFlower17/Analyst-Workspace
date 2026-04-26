# Analyst Workspace

Analyst Workspace is an intelligent analytics workspace for combining structured data, business context, and agent-driven analysis in one place.

The long-term goal of this project is to support:

- structured data analysis across multiple datasets
- workspace-based business context management
- artifact-style result persistence
- hybrid analysis with structured and unstructured context
- agent orchestration and evaluation harnesses

---

## Current Status

The project is currently in a transition phase:

- `frontend-next/` is the future frontend mainline
- `frontend/` is still kept as a migration-period fallback
- the current stable core is multi-dataset structured analysis
- unstructured document ingestion and retrieval have started, and minimal document-aware workspace analysis is now connected

---

## Repository Structure

```text
frontend-next/    Next.js frontend mainline
frontend/         Legacy Vue frontend
backend/          Spring Boot backend
python-executor/  FastAPI Python execution service
harness/          Minimum benchmark and evaluation harness
test-data/        Example datasets
docs/             Product, architecture, and roadmap documents
```

---

## Core Direction

This project is being built around one central idea:

**use natural language, workspace context, and execution tools to produce traceable, reusable analysis results**

In practice, that means the system is moving toward:

1. data ingestion
2. workspace organization
3. analysis planning
4. SQL / Python execution
5. result artifacts
6. hybrid structured + unstructured context

---

## Local Development

### Frontend Next

```bash
cd frontend-next
npm install
npm run dev
```

### Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Backend With Vector Retrieval

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local,local-vector
```

### Python Executor

```bash
cd python-executor
pip install -r requirements.txt
python app/main.py
```

---

## Validation

For the current `frontend-next` app:

```bash
cd frontend-next
npm run lint
npm run build
```

For the minimum Phase 1 harness:

```bash
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category structured
```

---

## Documentation

Start here:

- [docs/README.md](docs/README.md)
- [docs/phase1-closeout.md](docs/phase1-closeout.md)
- [docs/phase2-kickoff.md](docs/phase2-kickoff.md)
- [docs/frontend-next-migration.md](docs/frontend-next-migration.md)
- [docs/task-roadmap.md](docs/task-roadmap.md)

Module docs:

- [docs/modules/platform.md](docs/modules/platform.md)
- [docs/modules/frontend.md](docs/modules/frontend.md)
- [docs/modules/backend.md](docs/modules/backend.md)
- [docs/modules/auth.md](docs/modules/auth.md)
- [docs/modules/dataset.md](docs/modules/dataset.md)
- [docs/modules/chat.md](docs/modules/chat.md)
- [docs/modules/workspace.md](docs/modules/workspace.md)
- [docs/modules/analysis.md](docs/modules/analysis.md)
- [docs/modules/python-executor.md](docs/modules/python-executor.md)

---

## Recommended GitHub Description

Intelligent analytics workspace for structured data, workspace context, and agent-driven analysis.
