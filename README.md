# RoomMatrix 🏨🏢

RoomMatrix is a comprehensive, modern Property & Room Management Platform designed to streamline operations for tenants, property owners, and managers. Built as a monorepo featuring a high-performance Next.js frontend and a production-grade Spring Boot 4 backend with clean architecture, strict security standards, and comprehensive automated test coverage.

---

## 🚀 Tech Stack

### Backend
- **Framework**: Spring Boot 4.1.2 (Java 21)
- **Security**: Spring Security 7 (Stateless JWT, BCrypt, DaoAuthenticationProvider)
- **Database & ORM**: MySQL 8.x, Spring Data JPA, Hibernate ORM
- **Authentication**: Two-Step Registration with WhatsApp OTP verification
- **Validation**: Jakarta Bean Validation (`spring-boot-starter-validation`)
- **JSON & Utilities**: Hibernate Native JSON Types (`@JdbcTypeCode(SqlTypes.JSON)`), Lombok, JJWT 0.12.6
- **Testing**: JUnit 5 Jupiter, Mockito, Spring MockMvc

### Frontend
- **Framework**: Next.js 16 (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **Icons & UI**: Lucide React
- **Forms & Validation**: React Hook Form, Zod

---

## 🏗️ System Architecture & Monorepo Structure

```
RoomMatrix/
├── backend/                               # Spring Boot 4 Java 21 Backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/roommatrix/backend/
│   │   │   │   ├── config/               # SecurityConfig, CorsConfig
│   │   │   │   ├── controller/           # REST Controllers (AuthController)
│   │   │   │   ├── dto/auth/             # Request & Response records with validations
│   │   │   │   ├── entity/               # JPA Entities (User, PendingRegistration)
│   │   │   │   │   └── enums/            # Domain Enums (Role, Gender)
│   │   │   │   ├── exception/            # Domain Exceptions & GlobalExceptionHandler
│   │   │   │   ├── repository/           # Spring Data JPA Repositories
│   │   │   │   ├── security/             # JwtService, CustomUserDetails, JwtAuthenticationFilter
│   │   │   │   └── service/              # Business Logic & WhatsAppService
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       ├── application-dev.properties
│   │   │       └── application-prod.properties
│   │   └── test/                         # 100% Pass: JwtServiceTest, AuthServiceTest, AuthControllerTest
│   └── pom.xml
├── frontend/                              # Next.js 16 React App
│   ├── src/app/                          # App Router pages and layouts
│   ├── public/                           # Static assets
│   ├── package.json
│   └── tsconfig.json
├── .gitignore                             # Monorepo-level gitignore
└── README.md
```

---

## 🔐 Two-Step Registration with WhatsApp OTP

RoomMatrix implements an enterprise-grade two-step verification flow engineered to prevent **Ghost Registration / Account Lockout** attacks:

```
[ Client ]                        [ Backend ]                     [ WhatsApp Service ]
    |                                  |                                   |
    |-- 1. POST /api/auth/register --->|                                   |
    |      (Name, Email, Mobile, Pwd)  |-- Validates input                 |
    |                                  |-- Hashes pwd with BCrypt          |
    |                                  |-- Saves to pending_registrations  |
    |                                  |-- Generates 6-digit OTP --------->| Sends OTP
    |<- Returns 200 OK (OTP Sent) -----|                                   |
    |                                  |                                   |
    |-- 2. POST /api/auth/verify-otp ->|                                   |
    |      (Mobile, 6-digit OTP)       |-- Validates OTP & Expiry (10 min) |
    |                                  |-- Atomically saves to real users  |
    |                                  |-- Deletes pending registration    |
    |                                  |-- Issues Access Token (Header)    |
    |<- Returns 201 Created -----------|-- Sets Refresh Token (HttpOnly)   |
```

### Why Pending Registration?
In traditional registration, writing unverified users directly into the `users` table with `isVerified = false` enables malicious actors to lock out real users by submitting dummy passwords with valid phone numbers. 

RoomMatrix completely eliminates this risk:
- All unverified submissions are stored in an ephemeral `pending_registrations` table with a **10-minute auto-expiry**.
- If a user re-submits registration before verifying, the pending record is **safely overwritten** with fresh OTP credentials.
- The real `users` table contains **only verified, authenticated accounts**.

---

## 📡 API Reference

### Authentication Endpoints

#### 1. Initiate Registration
- **Endpoint**: `POST /api/auth/register`
- **Request Body**:
```json
{
  "firstName": "John",
  "middleName": "A",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "StrongPassword123!",
  "mobileNumber": "9876543210",
  "gender": "MALE",
  "roles": ["TENANT"]
}
```
- **Response** (`200 OK`):
```json
{
  "message": "OTP sent to WhatsApp successfully. Please verify within 10 minutes.",
  "mobileNumber": "9876543210",
  "expiresInMinutes": 10
}
```

#### 2. Verify WhatsApp OTP
- **Endpoint**: `POST /api/auth/verify-otp`
- **Request Body**:
```json
{
  "mobileNumber": "9876543210",
  "otp": "582910"
}
```
- **Response** (`201 Created`):
  - **Headers**: `Set-Cookie: refreshToken=...; HttpOnly; Path=/; SameSite=Lax`
  - **Body**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1Ni...",
  "tokenType": "Bearer",
  "firstName": "John",
  "lastName": "Doe",
  "fullName": "John A Doe",
  "email": "john.doe@example.com",
  "mobileNumber": "9876543210",
  "gender": "MALE",
  "roles": ["TENANT"],
  "roleReferences": {
    "TENANT": null
  }
}
```

#### 3. Resend OTP
- **Endpoint**: `POST /api/auth/resend-otp`
- **Request Body**:
```json
{
  "mobileNumber": "9876543210"
}
```
- **Response** (`200 OK`):
```json
{
  "message": "A fresh OTP has been sent to your WhatsApp.",
  "mobileNumber": "9876543210",
  "expiresInMinutes": 10
}
```

#### 4. Login (Mobile Number & Password)
- **Endpoint**: `POST /api/auth/login`
- **Request Body**:
```json
{
  "mobileNumber": "9876543210",
  "password": "StrongPassword123!"
}
```
- **Response** (`200 OK`):
  - **Headers**: `Set-Cookie: refreshToken=...; HttpOnly; Path=/; SameSite=Lax`
  - **Body**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1Ni...",
  "tokenType": "Bearer",
  "firstName": "John",
  "lastName": "Doe",
  "fullName": "John A Doe",
  "email": "john.doe@example.com",
  "mobileNumber": "9876543210",
  "gender": "MALE",
  "roles": ["TENANT"],
  "roleReferences": {
    "TENANT": null
  }
}
```

---

## 🛡️ Security & Clean Architecture

- **Stateless Authentication**: Access tokens expire in 15 minutes; Refresh tokens are securely held in HTTP-only, SameSite=Lax cookies for 7 days.
- **Zero Credentials in Git**: All sensitive credentials, database passwords, and secrets are strictly resolved from environment variables (`${VAR_NAME}`) and never hardcoded in source control.
- **Extensible Multi-Role Architecture**: The `roleReferences` field is persisted as a native MySQL JSON column (`@JdbcTypeCode(SqlTypes.JSON)`), allowing a single user account to hold multiple roles (e.g., both Owner and Tenant) without database join table overhead.
- **Standardized Error Handling**: Centralized [`GlobalExceptionHandler`](backend/src/main/java/com/roommatrix/backend/exception/GlobalExceptionHandler.java) converts validation failures and domain exceptions into unified JSON responses.

---

## ⚙️ Local Development Setup

### Prerequisites
- **Java**: OpenJDK 21 or later
- **Node.js**: v18.x or later
- **Database**: MySQL 8.0+

### 1. Environment Configuration

Define the following environment variables (e.g., in `.vscode/launch.json` or your shell):

```bash
DATABASE_URL=jdbc:mysql://localhost:3306/room_matrix
DATABASE_USER=root
DATABASE_PASSWORD=your_password
activeProfile=dev
ALLOWED_ORIGINS=http://localhost:3000
JWT_SECRET=your_base64_encoded_256_bit_secret_key
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

# WhatsApp Provider Configuration (console for dev, meta for production)
WHATSAPP_PROVIDER=console
WHATSAPP_PHONE_NUMBER_ID=your_meta_phone_number_id
WHATSAPP_ACCESS_TOKEN=your_meta_system_user_access_token
WHATSAPP_TEMPLATE_NAME=roommatrix_otp_verification
```

#### Meta WhatsApp Cloud API Setup (Production)
1. In the [Meta for Developers Portal](https://developers.facebook.com/), create an app with the **WhatsApp Business** product.
2. Under WhatsApp > API Setup, copy your **Phone Number ID**.
3. Generate a permanent System User **Access Token** with `whatsapp_business_messaging` permissions.
4. Create an authentication/utility template named `roommatrix_otp_verification` with a body variable `{{1}}` for the OTP.
5. Set `WHATSAPP_PROVIDER=meta` in your production environment variables. In local development, leaving `WHATSAPP_PROVIDER=console` prints OTPs directly to your terminal.

### 2. Run Backend
```bash
cd backend
./mvnw spring-boot:run
```

### 3. Run Backend Tests
```bash
cd backend
./mvnw test
```

### 4. Run Frontend
```bash
cd frontend
npm install
npm run dev
```

Frontend runs on [http://localhost:3000](http://localhost:3000) and Backend runs on [http://localhost:8080](http://localhost:8080).

---

## 🧪 Testing Summary

The backend features comprehensive automated test coverage across all layers:
- **`AuthControllerTest`** (12 tests): Complete MockMvc testing of HTTP status codes, validation constraints, login, OTP verification, and cookie handling.
- **`AuthServiceTest`** (11 tests): Comprehensive unit testing of OTP generation, WhatsApp dispatch, user lifecycle, mobile login, and race condition prevention.
- **`MetaWhatsAppServiceTest`** (8 tests): Verification of Meta Graph API request construction, Bearer auth, phone number E.164 normalization, and error handling.
- **`JwtServiceTest`** (10 tests): Verification of token generation, signature validation, type claims, and expiration.

---

## 📄 License
This project is licensed under the MIT License.
