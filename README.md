# Repwise

> Track every rep. Train smarter.

Repwise is a fitness tracking platform built as a set of independent Spring Boot microservices. Services discover each other through a Eureka registry, own their own data stores, and communicate over load-balanced HTTP.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.4-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-users-336791)
![MongoDB](https://img.shields.io/badge/MongoDB-activities%20%26%20recommendations-47A248)

## Architecture

```
                        +--------------------+
                        |   Eureka Server    |
                        |     port 8761      |
                        +---------+----------+
                                  ^
              register / discover |
        +-------------------------+-------------------------+
        |                         |                         |
+-------+--------+      +---------+--------+      +---------+--------+
|  User Service  |<-----+ Activity Service |      |    AI Service    |
|   port 8000    | HTTP |    port 8001     |      |    port 8002     |
|  (PostgreSQL)  |      |    (MongoDB)     |      |    (MongoDB)     |
+----------------+      +------------------+      +------------------+
```

Each service is a self-contained Maven project with its own wrapper, build, configuration and database. There is deliberately no shared parent POM or shared library, so any service can be built, tested, deployed and scaled on its own.

## Services

| Service | Directory | Port | Datastore | Status |
|---|---|---|---|---|
| Eureka Server | `eureka` | 8761 | none | Working |
| User Service | `userservice` | 8000 | PostgreSQL | Working |
| Activity Service | `activityservice` | 8001 | MongoDB | Working |
| AI Service | `aiservice` | 8002 | MongoDB | In progress |

### Ports

Each service has a fixed port so they can all run side by side locally. Change one with `server.port` in the service's `application.properties` (or `SERVER_PORT` in the environment).

| Service | Port | URL |
|---|---|---|
| Eureka Server | 8761 | http://localhost:8761 |
| User Service | 8000 | http://localhost:8000 |
| Activity Service | 8001 | http://localhost:8001 |
| AI Service | 8002 | http://localhost:8002 |

### Eureka Server
Service registry that all other services register with. It does not register itself and does not fetch a registry.

### User Service
Handles registration and profile lookup, with Bean Validation on incoming requests (valid email, password of at least 6 characters). Users are stored in PostgreSQL through Spring Data JPA, with generated UUID identifiers, creation and update timestamps, and a `USER` or `ADMIN` role. Also exposes a lightweight validation endpoint used by other services.

### Activity Service
Records and retrieves workouts. Before saving an activity it calls the User Service through a load-balanced `WebClient` (`http://USER-SERVICE`, resolved via Eureka) to confirm the user exists, and rejects the request if not. Activities are stored in MongoDB and support a free-form `metrics` map for type-specific data.

Supported activity types: `RUNNING`, `SWIMMING`, `WALKING`, `CYCLING`, `WEIGHT_LIFTING`, `PUNCHING_BAG`, `ROPE_SKIPPING`, `AEROBICS`.

### AI Service
Serves personalised recommendations for logged activities. The data model (improvements, suggestions and safety guidance per activity), the MongoDB repository, and endpoints to fetch recommendations by user or by activity are in place. Generating the recommendations themselves is not yet implemented.

## API Reference

### User Service (`:8000`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/users/register` | Register a new user |
| `GET` | `/api/users/{userId}` | Get a user profile |
| `GET` | `/api/users/{userId}/validate` | Returns `true` if the user exists |

```json
POST /api/users/register
{
  "email": "jane@example.com",
  "password": "secret123",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

### Activity Service (`:8001`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/activities` | Track a new activity |
| `GET` | `/api/activities` | List activities for the user in the `X-User-ID` header |
| `GET` | `/api/activities/{activityId}` | Get a single activity |

```json
POST /api/activities
{
  "userId": "<user id>",
  "type": "RUNNING",
  "duration": 45,
  "caloriesBurned": 420,
  "startTime": "2026-10-29T06:30:00",
  "additionalMetrics": { "distanceKm": 7.2 }
}
```

### AI Service (`:8002`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/recommendations/user/{userId}` | List a user's recommendations |
| `GET` | `/api/recommendations/activity/{activityId}` | Get the recommendation for an activity |

## Tech Stack

- Java 21, Spring Boot 4.1.2, Spring Cloud 2025.1.4
- Spring Web MVC, Spring WebFlux (`WebClient`), Spring Cloud Netflix Eureka
- Spring Data JPA with PostgreSQL, Spring Data MongoDB
- Jakarta Bean Validation, Lombok
- Maven (wrapper included in every service)

## Getting Started

### Prerequisites

- JDK 21
- PostgreSQL running on `localhost:5432` with a database named `repwise_users_db`
- A MongoDB instance (local or Atlas)

### Configuration

Secrets are read from environment variables, never committed.

| Variable | Used by | Purpose |
|---|---|---|
| `DB_USERNAME` | User Service | PostgreSQL username |
| `DB_PASSWORD` | User Service | PostgreSQL password |
| `MONGODB_URI` | Activity Service, AI Service | MongoDB connection string |

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export MONGODB_URI="mongodb://localhost:27017/repwise"
```

### Running

Start the registry first, then the rest in any order. Each in its own terminal:

```bash
cd eureka          && ./mvnw spring-boot:run
cd userservice     && ./mvnw spring-boot:run
cd activityservice && ./mvnw spring-boot:run
cd aiservice       && ./mvnw spring-boot:run
```

Open the Eureka dashboard at http://localhost:8761 to confirm the services have registered.

### Try it

```bash
# 1. Register a user and note the returned id
curl -X POST localhost:8000/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"secret123","firstName":"Jane","lastName":"Doe"}'

# 2. Track an activity for that user
curl -X POST localhost:8001/api/activities \
  -H "Content-Type: application/json" \
  -d '{"userId":"<id>","type":"RUNNING","duration":45,"caloriesBurned":420,"startTime":"2026-10-29T06:30:00"}'

# 3. List the user's activities
curl localhost:8001/api/activities -H "X-User-ID: <id>"
```

## Project Structure

```
repwise/
├── eureka/            Service registry
├── userservice/       Users and profiles (PostgreSQL)
├── activityservice/   Workout tracking (MongoDB)
├── aiservice/         Recommendations (MongoDB, generation in progress)
└── README.md
```

Each service follows the same layered layout: `controller`, `service`, `repository`, `model`, `dto` and `config`.

## Roadmap

- [ ] Implement AI recommendation generation for logged activities
- [ ] Event-driven flow between Activity and AI services (for example via a message broker)
- [ ] API gateway as a single entry point
- [ ] Authentication and authorisation
- [ ] Centralised error handling with proper HTTP status codes
- [ ] Containerisation with Docker Compose
- [ ] Unit and integration test coverage

## Author

Karabo Charles Matsepane
