# IT Ticket Auto-Routing & SLA Management Platform

An IT ticket management platform built with Spring Boot, Spring Security, JWT, JPA / Hibernate, PostgreSQL, and vanilla frontend.

---

## Architecture Overview

### Phase 1 Foundation
```text
Browser / Vanilla HTML+JS Fetch
              ↓
    Spring Boot REST API
              ↓
          Controller
              ↓
           Service
              ↓
         Repository
              ↓
       JPA / Hibernate
              ↓
          PostgreSQL
```

### Phase 2 Architecture (Security & RBAC)
```text
                    USER
                      |
                      v
             Login / Register
                      |
                      v
              Spring Boot API
                      |
              Spring Security
                      |
             +--------+--------+
             |                 |
        Public APIs       Protected APIs
             |                 |
             v                 v
       Authentication      JWT Filter
             |                 |
             v                 v
        JWT generated     SecurityContext
                               |
                               v
                         Authorization
                               |
                               v
                          Controller
                               |
                               v
                            Service
                               |
                               v
                          Repository
                               |
                               v
                          PostgreSQL
```

---

## Project Structure

```text
backend/pom.xml                                          Maven dependencies (Spring Boot, Security, JJWT)
backend/src/main/resources/application.properties        Database and JWT configuration
backend/src/main/java/com/ticketing/
  ├── TicketApplication.java                             Spring Boot bootstrap & demo user seed
  ├── controller/
  │     ├── AuthController.java                          POST /api/auth/register, POST /api/auth/login
  │     └── TicketController.java                        POST /api/tickets, GET /api/tickets, GET /api/tickets/{id}
  ├── dto/
  │     ├── AuthResponse.java                            Auth payload (token, userId, name, email, role)
  │     ├── CreateTicketRequest.java                     Ticket creation input validation
  │     ├── LoginRequest.java                            Login credentials DTO
  │     ├── RegisterRequest.java                         Public registration DTO (defaults to EMPLOYEE)
  │     └── TicketResponse.java                          Ticket output projection (with owner info)
  ├── entity/
  │     ├── Role.java                                    Enum: EMPLOYEE, AGENT, ADMIN
  │     ├── Ticket.java                                  Ticket entity (ManyToOne -> User)
  │     └── User.java                                    User entity (table: users, BCrypt password)
  ├── exception/
  │     ├── DuplicateEmailException.java                 409 Conflict on duplicate registration
  │     └── GlobalExceptionHandler.java                  Centralized exception handling (400, 401, 403, 404, 409, 500)
  ├── repository/
  │     ├── TicketRepository.java                        Ticket data access & findByUserId
  │     └── UserRepository.java                          User data access & findByEmail
  ├── security/
  │     ├── JwtAuthenticationFilter.java                 OncePerRequestFilter: validates Bearer token
  │     └── SecurityConfig.java                          SecurityFilterChain, CORS, stateless session, CSRF
  └── service/
        ├── AuthService.java                             Registration & login verification
        ├── CustomUserDetailsService.java                Loads UserDetails for Spring Security
        ├── JwtService.java                              JWT generation, claim extraction, validation
        └── TicketService.java                           Ownership enforcement & RBAC ticket queries
backend/src/test/java/com/ticketing/
  └── TicketApplicationTests.java                        Comprehensive integration tests
frontend/
  ├── index.html                                         Vanilla HTML UI with Auth & Workspace tabs
  ├── style.css                                          Custom stylesheet with role badges & tables
  └── app.js                                             Session storage, apiFetch wrapper & DOM logic
```

---

## Database Model & Relationships

### Users Table (`users`)
```text
users
--------------------------------
id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY
name        VARCHAR(100) NOT NULL
email       VARCHAR(150) NOT NULL UNIQUE
password    VARCHAR(255) NOT NULL (BCrypt hash)
role        VARCHAR(20) NOT NULL (EMPLOYEE, AGENT, ADMIN)
created_at  TIMESTAMP WITH TIME ZONE NOT NULL
```

### Tickets Table (`tickets`)
```text
tickets
--------------------------------
id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY
subject     VARCHAR(200) NOT NULL
description TEXT NOT NULL
status      VARCHAR(30) NOT NULL
created_at  TIMESTAMP WITH TIME ZONE NOT NULL
user_id     BIGINT REFERENCES users(id)
```

### Relationship: `User 1 -------- * Ticket`
One `User` has many `Ticket`s. Each ticket belongs to an owner (`user_id` foreign key).

---

## Authentication & Authorization Flows

### 1. User Registration Flow
```text
Register (POST /api/auth/register)
   ↓
Validate input (name, email, password >= 6 chars)
   ↓
Check duplicate email (throws 409 if exists)
   ↓
Hash password using BCrypt (PasswordEncoder)
   ↓
Role set strictly to EMPLOYEE (cannot choose ADMIN/AGENT publicly)
   ↓
Save to PostgreSQL 'users' table
   ↓
Generate JWT token
   ↓
Return AuthResponse (token, userId, name, email, role)
```

### 2. User Login Flow
```text
Login (POST /api/auth/login)
   ↓
Lookup user by email in PostgreSQL
   ↓
Verify password using PasswordEncoder.matches(enteredPassword, storedHash)
   ↓
If invalid: return 401 Unauthorized ("Invalid email or password")
   ↓
If valid: generate signed JWT (subject=email, role=ROLE, exp=24h)
   ↓
Return AuthResponse to client
```

### 3. Protected Request Flow (Bearer Token)
```text
Client Request (e.g. GET /api/tickets/1)
   ↓
Authorization Header: "Bearer <token>"
   ↓
JwtAuthenticationFilter (OncePerRequestFilter)
   ↓
Extract & validate JWT signature + expiration
   ↓
Extract username (email) and load UserDetails
   ↓
Create UsernamePasswordAuthenticationToken
   ↓
Store Authentication in SecurityContext
   ↓
Authorization Check (Controller / Service):
   - EMPLOYEE: Can only access own tickets (ownership check)
   - AGENT: Can view all tickets in queue
   - ADMIN: Can view and manage all tickets
   ↓
If forbidden: return 403 Forbidden
If allowed: execute business logic and return response
```

---

## Core Security & Architecture Concepts Explained

* **Authentication vs Authorization**:
  * *Authentication* answers: *"Who are you?"* (verifying credentials and issuing a token).
  * *Authorization* answers: *"What are you allowed to do?"* (verifying if an EMPLOYEE is allowed to read ticket #5).
* **JWT (JSON Web Token)**: A compact, URL-safe token used for stateless authentication.
  * **Structure**: `Header.Payload.Signature`
    * *Header*: Specifies algorithm (`HS256`/`HS512`) and token type (`JWT`).
    * *Payload*: Claims containing identity (`subject = email`), assigned role (`role = EMPLOYEE`), issued-at (`iat`), and expiration (`exp`). No sensitive data or passwords are stored in the payload.
    * *Signature*: Cryptographic hash of `Base64(Header) + "." + Base64(Payload)` signed with the secret key (`HMAC-SHA`). Ensures the token cannot be tampered with by clients.
  * **Expiration**: Enforces token lifespan (configured via `jwt.expiration-ms`, defaults to 24 hours).
* **Bearer Token**: An HTTP authorization scheme where the client provides the token in the `Authorization: Bearer <token>` request header.
* **BCrypt & PasswordEncoder**: BCrypt is an adaptive cryptographic one-way hashing function incorporating a random salt and computational cost factor. It protects against rainbow table and brute-force attacks. Passwords are never stored in plaintext or reversibly encrypted.
* **SecurityFilterChain**: Spring Security 6's modern component-based filter pipeline that intercepts and secures every incoming HTTP request.
* **OncePerRequestFilter (`JwtAuthenticationFilter`)**: Ensures the JWT extraction and validation logic executes exactly once per HTTP request dispatch.
* **SecurityContext**: Spring Security's thread-local storage holding the current `Authentication` object (principal, credentials, and authorities) for the lifespan of the request.
* **UserDetails & UserDetailsService**: `UserDetailsService` fetches application-specific user data (`CustomUserDetailsService`), while `UserDetails` wraps the principal credentials and granted authorities (`ROLE_EMPLOYEE`, `ROLE_ADMIN`, etc.) for Spring Security.
* **RBAC (Role-Based Access Control)**:
  * `EMPLOYEE`: Can create tickets and view only their own tickets. Attempting to view another user's ticket results in `403 Forbidden`.
  * `AGENT`: Can view tickets across the support queue.
  * `ADMIN`: Has full visibility to view all tickets in the system.
* **401 Unauthorized vs 403 Forbidden**:
  * `401 Unauthorized`: The client is unauthenticated (missing, invalid, or expired JWT, or incorrect login credentials).
  * `403 Forbidden`: The client is authenticated, but lacks sufficient permissions to access the requested resource (e.g. an employee requesting another employee's ticket).
* **Stateless Authentication**: The backend does not maintain server-side HTTP sessions (`SessionCreationPolicy.STATELESS`). Every request is authenticated independently via its JWT Bearer token, enabling horizontal scalability.
* **CORS (Cross-Origin Resource Sharing)**: Allows the frontend running on `http://localhost:5500` (or `http://127.0.0.1:5500`) to communicate with the Spring Boot backend on `http://localhost:8080`, with preflight `OPTIONS` requests permitted.

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/it_ticket_platform` |
| `DB_USERNAME` | Database username | `postgres` (or `$(whoami)` on macOS Homebrew) |
| `DB_PASSWORD` | Database password | *(empty)* |
| `JWT_SECRET` | 256-bit+ HMAC secret key | Pre-configured secure development key |
| `JWT_EXPIRATION_MS` | JWT validity in milliseconds | `86400000` (24 hours) |

---

## Running the Application

### 1. Database Setup
```bash
# Create database
createdb it_ticket_platform
```

Set environment variables if your username differs from `postgres`:
```bash
export DB_USERNAME=$(whoami)
export DB_PASSWORD=
export JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
```

### 2. Start the Backend
```bash
cd backend
mvn clean package -DskipTests
java -jar target/ticket-platform-backend-0.0.1-SNAPSHOT.jar
```
*Or via Maven:*
```bash
mvn spring-boot:run
```

### 3. Start the Frontend
```bash
cd frontend
python3 -m http.server 5500
```

Open: `http://localhost:5500`

---

## Pre-Seeded Demo Accounts

For interview demonstration, the application seeds 3 default accounts upon startup:

| Role | Email | Password | Permissions |
|---|---|---|---|
| **EMPLOYEE** | `employee@example.com` | `Employee123` | Create tickets, view own tickets |
| **AGENT** | `agent@example.com` | `Agent123` | View all tickets in system |
| **ADMIN** | `admin@example.com` | `Admin123` | View all tickets, full administrative visibility |

## Phase 3: Ticket Workflow

Phase 3 keeps the Phase 2 JWT authentication and adds a role-aware ticket workflow.

Ticket lifecycle:

```text
OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
```

Statuses are stored as enum strings. The backend rejects skipped, reversed, or repeated transitions. Employees may close their own resolved tickets, assigned agents may move tickets through in-progress and resolved, and administrators can manage the full workflow.

Role visibility and actions:

- `EMPLOYEE`: creates tickets and sees only tickets they created; may close a resolved ticket.
- `AGENT`: sees only tickets assigned to them; may move assigned tickets to `IN_PROGRESS` or `RESOLVED`.
- `ADMIN`: sees all tickets, changes valid statuses, and assigns or unassigns agents.

Phase 3 ticket APIs:

```text
POST  /api/tickets
GET   /api/tickets?keyword=vpn&status=OPEN
GET   /api/tickets/{id}
PATCH /api/tickets/{id}/status       { "status": "IN_PROGRESS" }
PATCH /api/tickets/{id}/assign       { "agentId": 2 }
PATCH /api/tickets/{id}/unassign
```

Ticket responses include the owner, assigned agent, `createdAt`, and `updatedAt`. Search and status filters are applied inside role-scoped repository queries. The vanilla frontend now adds status counts, debounced search, status filtering, assignment/status controls, and updated ticket details while retaining the existing Phase 2 authentication flow.

## Phase 4: ML Classification

Phase 4 adds a small inference service:

```text
Browser -> Spring Boot -> FastAPI -> TF-IDF + calibrated Linear SVM
                                  -> category + confidence
                         -> AUTO_CLASSIFIED or MANUAL_REVIEW
```

The production model is the calibrated `TF-IDF + LinearSVC` approach selected from the existing experiments. It uses 47,837 tickets across eight categories. The calibrated benchmark was 86.49% overall accuracy, 86.62% macro F1, and 86.49% weighted F1. SBERT-based alternatives were not selected because they performed worse on this dataset.

The initial operating threshold is `0.80`. At that threshold, validation showed 63.91% of predictions accepted automatically and 97.38% accuracy among those accepted predictions. The 97.38% figure is conditional validation accuracy, not overall model accuracy; overall test-set accuracy is 86.49%.

Training is offline. FastAPI loads the saved `ticket_classifier_calibrated.joblib` artifact once at startup and performs inference only. The repository intentionally does not include the 47,837-row dataset or a local model artifact. Provide the artifact through `MODEL_PATH` when running the service.

### FastAPI service

```bash
cd ml_service
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
MODEL_PATH=../model/ticket_classifier_calibrated.joblib \
ML_CONFIDENCE_THRESHOLD=0.80 \
.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

The service exposes `GET /health` and `POST /predict` with `{ "text": "..." }`. If the artifact is missing, health and prediction return `503` with a safe error message.

### Spring Boot integration

Spring Boot sends `subject + description` to `ML_SERVICE_URL` with a short timeout. Ticket creation succeeds even when FastAPI is unavailable: the ticket is retained with `MANUAL_REVIEW`. Confidence at or above `ML_CONFIDENCE_THRESHOLD` stores `category` and `AUTO_CLASSIFIED`; lower confidence stores `predictedCategory` and `mlConfidence` while leaving final `category` unset.

Relevant configuration:

```text
ML_SERVICE_URL=http://localhost:8000
ML_CONFIDENCE_THRESHOLD=0.80
ML_CONNECT_TIMEOUT_MS=1000
ML_READ_TIMEOUT_MS=3000
```

Agents and administrators can use `GET /api/tickets/review-queue` and `PATCH /api/tickets/{id}/classification` with `{ "category": "Access" }`. Manual classification changes the status to `MANUALLY_CLASSIFIED` while retaining the original ML recommendation and confidence. Employees cannot access either review operation.

## Phase 5: Automatic Team Routing

Team assignment is deterministic backend business logic, separate from ML category prediction:

| Category | Team |
|---|---|
| Hardware | Hardware Support |
| Access | IT Access |
| HR Support | HR Helpdesk |
| Purchase | Procurement |
| Storage | Storage/Infrastructure |
| Internal Project | Internal Projects |
| Administrative rights | Administrative Support |
| Miscellaneous | General Support |

`AUTO_CLASSIFIED` tickets are routed immediately. `MANUAL_REVIEW` tickets retain `predictedCategory` and `mlConfidence` but have no final team until an agent or administrator classifies them. Manual classification then runs the same routing service. Existing human `assignedAgent` behavior remains independent from `assignedTeam`.

Team APIs:

```text
GET /api/teams
GET /api/teams/{id}
```

## Phase 6: Priority and SLA Management

Priority is deterministic and explainable; it is not ML-based. Critical keywords such as complete service outage, security incident, production-wide failure, and emergency produce `CRITICAL`. Major availability/access impact produces `HIGH`, informational/minor/non-urgent wording produces `LOW`, and other tickets default to `MEDIUM`.

The seeded demo SLA rules are calendar-time targets and are configurable database records, not industry-standard claims:

| Priority | Target |
|---|---:|
| CRITICAL | 120 minutes |
| HIGH | 240 minutes |
| MEDIUM | 480 minutes |
| LOW | 1440 minutes |

The ticket stores its UTC `Instant` deadline and current SLA status. `AT_RISK` begins when 20% or less of the original target remains. An unresolved ticket past its deadline is `BREACHED`; resolved or closed tickets become `RESOLVED`. A Spring scheduled service checks unresolved tickets every minute by default and writes only changed statuses. Business hours, holidays, weekends, notifications, and escalation automation are intentionally out of scope.

Ticket responses now expose `assignedTeam`, `priority`, `slaDeadline`, `slaStatus`, and `slaTargetMinutes`. The frontend displays these alongside category/classification data and dashboard counts for manual review, at-risk, and breached tickets.

## Complete API Flow

```text
POST /api/tickets
  -> PENDING
  -> FastAPI category/confidence
  -> AUTO_CLASSIFIED or MANUAL_REVIEW
  -> category-to-team routing when final category exists
  -> deterministic priority
  -> active SLA rule and UTC deadline
  -> scheduled SLA monitoring
```

Relevant configuration:

```text
DB_URL=jdbc:postgresql://localhost:5432/it_ticket_platform
DB_USERNAME=<database user>
DB_PASSWORD=<database password>
JWT_SECRET=<secret>
ML_SERVICE_URL=http://localhost:8000
ML_CONFIDENCE_THRESHOLD=0.80
MODEL_PATH=./model/ticket_classifier_calibrated.joblib
SLA_MONITOR_DELAY_MS=60000
```

The Spring Boot service can be started with `mvn spring-boot:run` from `backend`. The frontend remains the existing vanilla app and can be served with `python3 -m http.server 5500` from `frontend`. Run Java tests with `mvn test`; run FastAPI tests with `cd ml_service && .venv/bin/python -m pytest -q` after installing `requirements.txt`. Full end-to-end ML prediction requires providing the real trained `.joblib` artifact through `MODEL_PATH`; this repository does not commit the dataset or artifact.

---

## API Examples (cURL)

### 1. Register a New User (Default EMPLOYEE)
```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Kamal",
    "email": "kamal@example.com",
    "password": "Password123"
  }'
```
Response (`201 Created`):
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": 4,
  "name": "Kamal",
  "email": "kamal@example.com",
  "role": "EMPLOYEE"
}
```

### 2. Login
```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "kamal@example.com",
    "password": "Password123"
  }'
```
Response (`200 OK`):
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": 4,
  "name": "Kamal",
  "email": "kamal@example.com",
  "role": "EMPLOYEE"
}
```

### 3. Create Ticket (Authenticated Employee)
```bash
curl -i -X POST http://localhost:8080/api/tickets \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "VPN connection failure",
    "description": "Unable to connect to the internal network."
  }'
```
Response (`201 Created`):
```json
{
  "id": 1,
  "subject": "VPN connection failure",
  "description": "Unable to connect to the internal network.",
  "status": "OPEN",
  "createdAt": "2026-09-24T12:00:00Z",
  "userId": 4,
  "userEmail": "kamal@example.com"
}
```

### 4. Get Tickets (Role-Filtered)
```bash
curl -i http://localhost:8080/api/tickets \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>"
```
* If logged in as `EMPLOYEE`: returns only the caller's tickets.
* If logged in as `AGENT` or `ADMIN`: returns all tickets.

### 5. Get Ticket By ID (Ownership Protected)
```bash
curl -i http://localhost:8080/api/tickets/1 \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>"
```
* If ticket belongs to user (or user is AGENT/ADMIN): `200 OK`.
* If ticket belongs to another employee: `403 Forbidden` (`{"status":403,"message":"Forbidden: You do not have permission to access this ticket"}`).
* If token is missing/invalid: `401 Unauthorized`.

---

## Automated Test Suite

Run the full automated test suite:
```bash
cd backend
DB_USERNAME=$(whoami) mvn test
```

Test Results:
* `testApplicationStarts`: Context bootstrapping & bean wiring.
* `testValidRegistration`: User creation, EMPLOYEE default role, BCrypt hashed password in PostgreSQL.
* `testDuplicateEmailRejected`: 409 Conflict returned on duplicate email.
* `testInvalidRegistrationInputRejected`: 400 Bad Request on blank/invalid fields.
* `testValidLoginSucceeds`: 200 OK with valid JWT and user info.
* `testInvalidPasswordRejected`: 401 Unauthorized on bad credentials.
* `testUnknownUserRejected`: 401 Unauthorized without user enumeration.
* `testMissingTokenReturns401`: 401 Unauthorized on protected endpoint without token.
* `testInvalidTokenReturns401`: 401 Unauthorized on malformed/tampered token.
* `testExpiredTokenReturns401`: 401 Unauthorized on expired token.
* `testEmployeeCanCreateTicket`: Ticket created with user foreign key relationship.
* `testEmployeeViewsOnlyOwnTickets`: Employee query filters out tickets of other users.
* `testEmployeeCannotAccessAnotherEmployeesTicket`: 403 Forbidden enforced on cross-employee ticket access.
* `testAdminCanViewAllTickets`: Admin permission allows viewing all tickets.
* `testAgentCanAccessTickets`: Agent permission allows accessing tickets.

Total: **15 tests run, 0 failures, 0 errors**.

---

## Future Phases

```text
Phase 1 -> Basic CRUD & Persistence (Completed)
Phase 2 -> Authentication, JWT, Roles & Ownership (Completed)
Phase 3 -> Ticket Management Enhancements & Status Workflow
Phase 4 -> FastAPI ML Classification
Phase 5 -> Auto-Routing
Phase 6 -> Priority & SLA
Phase 7 -> Integration, Reliability & Testing
Phase 8 -> Documentation & Interview Preparation
```
