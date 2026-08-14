# Maitri

**Maitri** is a hyperlocal community platform connecting residents and students in the **Peenya / Nagasandra area of Bengaluru, Karnataka, India** with trusted small local businesses and service providers.

---

## Project Status

> 🚧 **Active Development — Phase 1: Project Foundation**

| Phase | Status |
|-------|--------|
| Phase 1 — Project Foundation | 🔄 In Progress |
| Phase 2 — Frontend Foundation | ⏳ Pending |
| Phase 3 — Authentication | ⏳ Pending |
| Phase 4 — Category Module | ⏳ Pending |
| Phase 5 onwards… | ⏳ Pending |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | HTML5, Vanilla CSS, Vanilla JavaScript |
| Backend | Java 17, Spring Boot 3.2.x, Maven |
| Database | MongoDB (local), MongoDB Compass |
| Auth | JWT (JJWT 0.12.x), BCrypt |
| Version Control | Git + GitHub |
| Cloud (future) | AWS |

---

## Local Development Setup

### Prerequisites

Before running the project, install:

1. **Java 17** (JDK) — [Download](https://adoptium.net/)
2. **Maven 3.8+** — [Download](https://maven.apache.org/)
3. **MongoDB Community Server** — [Download](https://www.mongodb.com/try/download/community)
4. **MongoDB Compass** — [Download](https://www.mongodb.com/products/compass) (GUI for DB management)
5. **VS Code** with the **Live Server** extension (for frontend)

### Running the Backend

```bash
# 1. Make sure MongoDB is running on localhost:27017

# 2. Navigate to the backend directory
cd backend

# 3. Copy the example config and adjust if needed
copy src\main\resources\application-local.properties.example src\main\resources\application-local.properties

# 4. Run the application
mvn spring-boot:run

# 5. Verify it's running
# Open: http://localhost:8080/api/health
```

### Running the Frontend

1. Open `frontend/` in VS Code
2. Right-click `index.html` → **"Open with Live Server"**
3. The frontend will be available at `http://localhost:5500`

---

## Project Structure

```
Maitri_new/
├── backend/          ← Spring Boot Maven project (Java)
├── frontend/         ← HTML + CSS + JavaScript
├── docs/             ← Project documentation
├── .gitignore
├── .env.example      ← Documents required environment variables
└── README.md
```

---

## Environment Variables

See [`.env.example`](.env.example) for required environment variables.

**Never commit real credentials.** The `application-local.properties` and `application-prod.properties` files are gitignored.

---

## Documentation

| Document | Description |
|----------|-------------|
| [PROJECT_PLAN.md](docs/PROJECT_PLAN.md) | Development phases and roadmap |
| [REQUIREMENTS.md](docs/REQUIREMENTS.md) | Functional and non-functional requirements |
| [DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) | MongoDB collections and schema |
| [API.md](docs/API.md) | REST API reference |
| [USER_FLOW.md](docs/USER_FLOW.md) | User journey diagrams |
| [FEATURES.md](docs/FEATURES.md) | Feature list by role and status |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Deployment guide (local + AWS) |
| [SECURITY.md](docs/SECURITY.md) | Security architecture and decisions |

---

## Target Service Categories (Version 1)

- 🍛 Street Food
- 🧵 Tailors
- 🖨️ Printing & Xerox
- 📱 Mobile / Laptop Repair

---

## License

Private — All rights reserved. Not yet open-sourced.
