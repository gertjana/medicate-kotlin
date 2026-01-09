# Medicate - Medicine Tracking Application

A functional Kotlin REST API service built with Ktor framework, Arrow for functional programming, and Redis for backend storage. Features multi-user support with isolated data per user.

## Features

- 💊 **Medicine Management** - Track medicines with dosage, stock, and descriptions
- 📅 **Schedule Management** - Create schedules for taking medicines
- 📊 **Dosage History** - Record and track medicine intake
- 📈 **Adherence Tracking** - Monitor weekly adherence to schedules
- 👥 **Multi-User Support** - Each user has isolated data
- 🔐 **Simple Authentication** - Username-based login/register

## Quick Start

### Prerequisites
- Java 21+
- Redis server running on localhost:6379
- Node.js 18+ (for frontend)

### 1. Start Redis
```bash
redis-server
```

### 2. Start Backend
```bash
./gradlew run
```

### 3. Start Frontend (in new terminal)
```bash
cd frontend
npm install
npm run dev
```

### 4. Access Application
Open http://localhost:5173 in your browser

## Multi-User Support

### New Users
1. Click "Register" in top-right corner
2. Enter a username
3. Start adding medicines and schedules

### Existing Users
1. Click "Login"
2. Enter your username
3. Access your data

### Migrating Existing Data
If you have existing data from before multi-user support:

```bash
# Make script executable
chmod +x migrate-to-multiuser.sh

# Run migration (assigns all data to "admin" user)
./migrate-to-multiuser.sh test admin
```

See `MIGRATION-GUIDE.md` for detailed instructions.

## Documentation

- **[IMPLEMENTATION-SUMMARY.md](IMPLEMENTATION-SUMMARY.md)** - Complete implementation overview
- **[MULTIUSER.md](MULTIUSER.md)** - Multi-user architecture details
- **[MIGRATION-GUIDE.md](MIGRATION-GUIDE.md)** - Data migration instructions

## Configuration

The application can be configured via `src/main/resources/application.conf` or environment variables:

- `PORT` - Server port (default: 8080)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `APP_ENV` - Environment name for Redis keys (default: test)

## API Endpoints

All endpoints require `X-Username` header (automatically sent by frontend when logged in).

### User
- `POST /api/user/register` - Register new user
- `POST /api/user/login` - Login existing user

### Medicine
- `GET /api/medicine` - Get all medicines
- `POST /api/medicine` - Create medicine
- `PUT /api/medicine/{id}` - Update medicine
- `DELETE /api/medicine/{id}` - Delete medicine
- `POST /api/addstock` - Add stock to medicine

### Schedule
- `GET /api/schedule` - Get all schedules
- `POST /api/schedule` - Create schedule
- `PUT /api/schedule/{id}` - Update schedule
- `DELETE /api/schedule/{id}` - Delete schedule

### Daily & History
- `GET /api/daily` - Get today's schedule
- `POST /api/takedose` - Record dose taken
- `GET /api/history` - Get dosage history

### Analytics
- `GET /api/adherence` - Get weekly adherence
- `GET /api/lowstock` - Get low stock medicines

## Technology Stack

**Backend:**
- Kotlin 1.9.22
- Ktor 2.3.7
- Arrow 1.2.1 (Functional Programming)
- Lettuce 6.3.0 (Redis Client)
- Kotest 5.8.0 (Testing)

**Frontend:**
- SvelteKit
- TypeScript
- TailwindCSS

**Database:**
- Redis

## Development

### Running Tests
```bash
./gradlew test
```

### Building for Production
```bash
./gradlew build
```

## Redis Key Structure

Multi-user data isolation using:
```
{environment}:user:{username}:medicine:{medicineId}
{environment}:user:{username}:schedule:{scheduleId}
{environment}:user:{username}:dosagehistory:{dosageId}
```

Each user's data is completely isolated from other users.
