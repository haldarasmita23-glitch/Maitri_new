# Maitri — REST API Reference

> **Document Status**: Shell — populated as each module is implemented.

---

## Base URL

- **Local**: `http://localhost:8080/api`
- **Production**: `https://api.maitri.in/api` (future)

---

## Standard Response Format

All endpoints return this structure:

```json
{
  "success": true,
  "message": "Human-readable message",
  "data": { },
  "errors": null,
  "timestamp": "2026-08-13T14:30:00"
}
```

On error:
```json
{
  "success": false,
  "message": "What went wrong",
  "errors": ["Specific validation error 1", "Specific validation error 2"],
  "timestamp": "2026-08-13T14:30:00"
}
```

---

## Authentication

Include the JWT token in the Authorization header for all protected endpoints:
```
Authorization: Bearer <your-jwt-token>
```

---

## Implemented Endpoints

### Health Check

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/health` | None | Verify backend is running |

**Response Example**:
```json
{
  "success": true,
  "message": "Maitri backend is running.",
  "data": {
    "status": "UP",
    "service": "Maitri Backend",
    "version": "1.0.0",
    "environment": "local",
    "timestamp": "2026-08-13T14:30:00"
  },
  "timestamp": "2026-08-13T14:30:00"
}
```

---

## Planned Modules (documented as implemented)

- [ ] `/api/auth` — Authentication (Phase 3)
- [ ] `/api/users` — User management (Phase 6)
- [ ] `/api/vendors` — Vendor management (Phase 5)
- [ ] `/api/categories` — Categories (Phase 4)
- [ ] `/api/reviews` — Reviews (Phase 7)
- [ ] `/api/favourites` — Favourites (Phase 8)
- [ ] `/api/complaints` — Complaints (Phase 9)
- [ ] `/api/notifications` — Notifications (Phase 10)
- [ ] `/api/chats` — Chat/messages (Phase 11)
- [ ] `/api/admin` — Admin operations (Phase 12)
