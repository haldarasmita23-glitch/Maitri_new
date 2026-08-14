# Maitri — Deployment Guide

> **Document Status**: Phase 1 covers local setup only. Cloud deployment documented in Phase 16+.

---

## Local Development

### Prerequisites
1. Java 17 JDK
2. Maven 3.8+
3. MongoDB Community Server (running on localhost:27017)
4. MongoDB Compass (optional GUI)
5. VS Code + Live Server extension

### Steps

```bash
# 1. Clone the repository (once GitHub is set up)
git clone <repo-url>
cd Maitri_new

# 2. Set up local config
cd backend/src/main/resources
copy application-local.properties.example application-local.properties
# Edit application-local.properties if needed

# 3. Build the backend
cd ../../../..    # back to backend/
mvn clean package

# 4. Run the backend
mvn spring-boot:run
# OR
java -jar target/maitri-backend-1.0.0.jar

# 5. Verify
# Open: http://localhost:8080/api/health
# Expected: { "success": true, "data": { "status": "UP", ... } }

# 6. Run frontend
# Open frontend/ in VS Code
# Right-click index.html → Open with Live Server
# Frontend runs at: http://localhost:5500
```

---

## Production Deployment (AWS — Phase 16, Not Yet)

> ⚠️ Do not attempt production deployment until Phase 14 (testing) is complete and explicitly approved.

Production architecture will include:
- EC2 (Spring Boot backend)
- EC2 (MongoDB)
- S3 (frontend + images)
- ALB (HTTPS)
- CloudFront (CDN)
- Route 53 (DNS)
- CloudWatch (monitoring)

Full production deployment guide will be written in Phase 16.
