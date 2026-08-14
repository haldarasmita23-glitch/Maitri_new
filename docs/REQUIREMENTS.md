# Maitri — Requirements

> **Document Status**: Approved. Updated if requirements change.

---

## Functional Requirements

### User Features
- [ ] User registration
- [ ] User login / logout
- [ ] User profile view and edit
- [ ] Browse service categories
- [ ] Search for vendors
- [ ] Discover local vendors
- [ ] View vendor details, address, location, opening hours
- [ ] View ratings and reviews
- [ ] Submit ratings and reviews
- [ ] Add / remove vendors from favourites
- [ ] Raise complaints and view complaint status
- [ ] Contact / chat with vendors
- [ ] Receive relevant notifications

### Vendor Features
- [ ] Vendor registration and login / logout
- [ ] Vendor profile (shop name, owner name, category, description, address, location, phone, hours, images)
- [ ] View own ratings and reviews
- [ ] Receive and respond to complaints
- [ ] Contact / chat with users
- [ ] Vendor verification by Admin before becoming visible

### Admin Features
- [ ] Admin login
- [ ] Admin dashboard with statistics
- [ ] View and manage users
- [ ] View and manage vendors
- [ ] Approve / reject vendors
- [ ] Manage categories
- [ ] View and update complaints
- [ ] Platform monitoring

---

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Scalability | Designed for AWS horizontal scaling |
| Security | JWT auth, BCrypt passwords, role-based access |
| Maintainability | Clean layered architecture, documented code |
| Performance | Fast API responses, indexed MongoDB queries |
| Reliability | Proper error handling, no silent failures |
| Usability | Mobile-friendly, responsive, accessible forms |
| Deployability | Environment-specific config, no hardcoded secrets |

---

## Constraints

- Frontend: HTML, CSS, Vanilla JavaScript only (no React, Angular, Vue)
- Backend: Java 17 + Spring Boot 3.x + Maven only
- Database: MongoDB (local) — no MongoDB Atlas
- Cloud: AWS only (when deployed)
- No third-party chat services
- No map integration without explicit approval
