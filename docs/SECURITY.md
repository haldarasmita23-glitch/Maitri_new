# Maitri — Security Architecture

> **Document Status**: Living document. Updated as security is implemented.

---

## Security Principles

1. **Passwords are never stored as plain text** — BCrypt hashing (strength 12)
2. **No secrets in source code** — Environment variables / gitignored config files
3. **JWT-based stateless authentication** — No server-side sessions
4. **Role-based access control** — USER, VENDOR, ADMIN roles enforced at API level
5. **Input validation** — Bean Validation on all incoming requests
6. **Centralized error handling** — No internal details exposed to clients
7. **CORS restricted** — Only allowed frontend origins accepted

---

## Authentication Flow (Phase 3)

```
1. Client sends: POST /api/auth/login { email, password, role }
2. Backend: finds account → BCrypt.matches(password, storedHash)
3. Backend: generates JWT → { sub: email, id: ..., role: ..., exp: ... }
4. Client: stores token in localStorage
5. Client: sends Authorization: Bearer <token> with every request
6. Backend JWT Filter: validates token → sets SecurityContext
7. Spring Security: checks role on each protected endpoint
```

---

## Password Security

- Algorithm: BCrypt
- Strength: 12 (4096 iterations)
- Never logged, never returned in API responses
- Never stored or transmitted in plain text

---

## JWT Token

| Claim | Value |
|-------|-------|
| `sub` | user email |
| `id` | MongoDB ObjectId |
| `role` | USER / VENDOR / ADMIN |
| `iat` | issued at (Unix timestamp) |
| `exp` | expiry (24 hours default) |

- Secret: loaded from environment variable `JWT_SECRET`
- Library: JJWT 0.12.x

---

## Role Access Matrix

| Endpoint Group | USER | VENDOR | ADMIN |
|---------------|------|--------|-------|
| `/api/health` | ✅ | ✅ | ✅ |
| `/api/auth/**` | ✅ | ✅ | ✅ |
| `/api/categories` (read) | ✅ | ✅ | ✅ |
| `/api/vendors` (browse) | ✅ | ✅ | ✅ |
| `/api/users/me` | ✅ | ❌ | ✅ |
| `/api/vendors/me` | ❌ | ✅ | ✅ |
| `/api/reviews` (write) | ✅ | ❌ | ✅ |
| `/api/favourites` | ✅ | ❌ | ✅ |
| `/api/complaints` (raise) | ✅ | ❌ | ✅ |
| `/api/admin/**` | ❌ | ❌ | ✅ |

---

## What's NOT Yet Implemented

- [ ] JWT filter (Phase 3)
- [ ] Role enforcement (Phase 3)
- [ ] Account lockout on failed logins (future)
- [ ] Rate limiting (future)
- [ ] HTTPS (Phase 16 — production)
- [ ] Input sanitization for XSS (future)
- [ ] Audit logging (future)
