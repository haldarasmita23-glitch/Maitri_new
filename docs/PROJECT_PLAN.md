# Maitri — Project Plan

> **Document Status**: Phase 1 complete ✅. Phase 2 pending.

---

## Project Overview

Maitri is a hyperlocal community platform connecting residents and students in the Peenya/Nagasandra area of Bengaluru with trusted local businesses.

**Target area**: Peenya, Nagasandra, Bengaluru, Karnataka, India  
**Initial categories**: Street Food, Tailors, Printing & Xerox, Mobile/Laptop Repair

---

## Development Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Project Foundation | ✅ Complete |
| 2 | Frontend Foundation | ⏳ Pending |
| 3 | Authentication (JWT) | ⏳ Pending |
| 4 | Category Module | ⏳ Pending |
| 5 | Vendor Module | ⏳ Pending |
| 6 | User Module | ⏳ Pending |
| 7 | Reviews & Ratings | ⏳ Pending |
| 8 | Favourites | ⏳ Pending |
| 9 | Complaints | ⏳ Pending |
| 10 | Notifications | ⏳ Pending |
| 11 | Chat / Contact | ⏳ Pending |
| 12 | Admin Module | ⏳ Pending |
| 13 | Full Frontend–Backend Integration | ⏳ Pending |
| 14 | Testing & Security Hardening | ⏳ Pending |
| 15 | Cloud Architecture Review | ⏳ Pending |
| 16 | AWS Deployment | ⏳ Pending |
| 17 | Production Testing | ⏳ Pending |
| 18 | Monitoring & Maintenance | ⏳ Pending |

---

## Phase 1 — Project Foundation

**Goal**: Set up the project structure and verify the backend starts and connects to MongoDB.

**Deliverables**:
- [x] Project folder structure
- [x] Maven pom.xml with all dependencies
- [x] Spring Boot configuration (local + prod templates)
- [x] Security configuration (Phase 1 open; Phase 3 will lock down)
- [x] Standard API response wrapper (ApiResponse)
- [x] Global exception handler
- [x] Health check endpoint (GET /api/health)
- [x] Documentation shells
- [x] .gitignore
- [x] Build verification (mvn clean package) — BUILD SUCCESS in 27s
- [x] Runtime verification (Spring Boot started in 3.6s, MongoDB connected at localhost:27017)
- [x] Health endpoint test (HTTP 200 — GET /api/health returns `{"success":true,"status":"UP"}`)

---

*This document will be updated at the completion of each phase.*
