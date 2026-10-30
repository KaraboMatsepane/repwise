# Repwise

> Track every rep. Train smarter.

Repwise is a fitness tracking platform built as a set of independent Spring Boot microservices. Clients talk to a single API gateway. Services find each other through a Eureka registry, load their settings from a central config server, own their own data stores, and communicate over load-balanced HTTP and asynchronous RabbitMQ messages.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.4-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-users-336791)
![MongoDB](https://img.shields.io/badge/MongoDB-activities%20%26%20recommendations-47A248)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-messaging-FF6600)

## Architecture

```
                         client
                           |
                           v
                  +-----------------+         +-----------------+
                  |   API Gateway   |         |  Config Server  |
                  |    port 8080    |         |    port 8888    |
                  +--------+--------+         +--------+--------+
                           |                           ^
          routes by path   |   (gateway, user, activity and ai
        (lb:// via Eureka) |    services fetch their config at startup)
                           |
        +------------------+------------------+
        |                  |                  |
+-------+--------+  +------+---------+  +-----+----------+
|  User Service  |<-+ Activity Svc   |  |   AI Service   |
|   port 8000    |  |   port 8001    |  |   port 8002    |
|  (PostgreSQL)  |  |   (MongoDB)    |  |   (MongoDB)    |
+----------------+  +-------+--------+  +--+----------+--+
                            |              ^          |
                            | publish      | consume  | HTTPS
                            v              |          v
                     activity.exchange --> activity.queue    Gemini API
                              (RabbitMQ)

All services register with the Eureka Server (port 8761).
```

When an activity is saved, the Activity Service publishes it to RabbitMQ and returns immediately. The AI Service consumes it from the queue, asks Gemini for a personalised recommendation and stores the result.

Each service is a self-contained Maven project with its own wrapper, build and database. There is deliberately no shared parent POM or shared library, so any service can be built, tested, deployed and scaled on its own.

## Services

| Service | Directory | Port | Datastore | Status |
|---|---|---|---|---|
| Config Server | `config-server` | 8888 | none | Working |
| Eureka Server | `eureka` | 8761 | none | Working |
| API Gateway | `gateway` | 8080 | none | Working |
| User Service | `userservice` | 8000 | PostgreSQL | Working |
| Activity Service | `activityservice` | 8001 | MongoDB | Working |
| AI Service | `aiservice` | 8002 | MongoDB | In progress |
| RabbitMQ (broker) | external | 5672 (AMQP), 15672 (management UI) | none | Required |

Each service has a fixed port so they can all run side by side locally. Ports live in `server.port` in the service's file under `config-server/src/main/resources/configs/` (Eureka and the config server keep theirs in their own `application.properties`), and can be overridden with `SERVER_PORT` in the environment.

### Config Server
Central configuration for the Gateway, User, Activity and AI services. It serves property files from `config-server/src/main/resources/configs/` (Spring Cloud Config's `native` profile). A file's name must match the client's `spring.application.name`:

| File | Applies to |
|---|---|
| `application.properties` | every client: Eureka URL, RabbitMQ connection, and the queue, exchange and routing key names |
| `gateway.properties` | Gateway: port and routes |
| `user-service.properties` | User Service: port, PostgreSQL |
| `activity-service.properties` | Activity Service: port, MongoDB |
| `ai-service.properties` | AI Service: port, MongoDB, Gemini |

Each client keeps only `spring.application.name` and `spring.config.import=configserver:...` in its own `application.properties` and fetches the rest at startup. Because the RabbitMQ names live in one shared file, the Activity and AI services cannot drift apart.

Secrets are not stored in the config files. Values such as `${MONGODB_URI}` are served unresolved and each client fills them in from its own environment variables, so the config server never needs them. Changes to the files take effect after the config server is rebuilt and the clients are restarted.

### Eureka Server
Service registry that all other services register with. It does not register itself, does not fetch a registry, and is deliberately not a config client, so the registry does not depend on the config server.

### API Gateway
Single entry point for clients (reactive Spring Cloud Gateway Server WebFlux, running on Netty). It registers with Eureka and forwards requests by path to load-balanced `lb://` URIs resolved through the registry. Routes are defined in `gateway.properties`:

| Path | Forwarded to |
|---|---|
| `/api/users/**` | `USER-SERVICE` |
| `/api/activities/**` | `ACTIVITY-SERVICE` |
| `/api/recommendations/**` | `AI-SERVICE` |

Paths that match no route return 404. Headers such as `X-User-ID` pass through unchanged. If no instance of a target service is registered, the gateway returns 503. Right after a service starts there can be a short delay (about half a minute) before the gateway sees it. The services stay reachable directly on their own ports.

### User Service
Handles registration and profile lookup, with Bean Validation on incoming requests (valid email, password of at least 6 characters). Passwords are hashed with BCrypt before they are stored and are never returned in responses. Users are stored in PostgreSQL through Spring Data JPA, with generated UUID identifiers, creation and update timestamps, and a `USER` or `ADMIN` role. It also exposes a lightweight validation endpoint used by other services.

### Activity Service
Records and retrieves workouts. Before saving an activity it calls the User Service through a load-balanced `WebClient` (`http://USER-SERVICE`, resolved via Eureka) to confirm the user exists, and rejects the request if not. Activities are stored in MongoDB and support a free-form `metrics` map for type-specific data.

After saving, it publishes the activity as JSON to RabbitMQ (exchange `activity.exchange`, routing key `activity.tracking`). A failure to publish is logged but does not fail the request, so the activity is still saved.

Requests are validated before anything else happens: `userId`, `type`, `duration` (greater than 0), `caloriesBurned` (0 or more) and `startTime` are required.

Supported activity types: `RUNNING`, `SWIMMING`, `WALKING`, `CYCLING`, `WEIGHT_LIFTING`, `PUNCHING_BAG`, `ROPE_SKIPPING`, `AEROBICS`.

### AI Service
Generates and serves personalised recommendations for logged activities.

It listens on `activity.queue` for activities published by the Activity Service, builds a prompt from each one and calls the Gemini API through `WebClient`. The reply is parsed into a `Recommendation` and saved to MongoDB. A recommendation contains a structured analysis (overall, pace, heart rate, calories) plus lists of improvements, suggestions and safety notes. Endpoints return recommendations by user or by activity.

Known gaps:
- A message whose processing fails (Gemini error, unparseable reply, database error) is requeued immediately and retried without limit. Retry limits and a dead-letter queue are not configured.
- A redelivered message can produce a second recommendation for the same activity.

## Error Handling

Each service with a REST API has a `@RestControllerAdvice` class (`exception/GlobalExceptionHandler`) that turns exceptions into a consistent JSON body. Services throw specific exceptions; only the handler chooses status codes. Anything unexpected is logged with its stack trace and returned as a generic 500, so internal details are not exposed.

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "User not found: abc",
  "path": "/api/users/abc",
  "timestamp": "2026-10-31T09:12:00",
  "details": []
}
```

`details` lists per-field messages for validation errors and is empty otherwise.

| Status | When |
|---|---|
| `400` | Validation failed, malformed JSON or unknown enum value, or a missing `X-User-ID` header |
| `404` | User, activity or recommendation not found, or the user in an activity request does not exist |
| `409` | Registering an email that is already taken |
| `503` | The Activity Service cannot reach the User Service, or it responds with an error |
| `500` | Any unhandled error |

Errors raised inside the RabbitMQ listener of the AI Service are not HTTP errors and are not covered by this.

## API Reference

Every endpoint is available through the gateway at `localhost:8080` and directly on the service's own port.

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
- Spring Web MVC, Spring WebFlux (`WebClient`)
- Spring Cloud Gateway (WebFlux), Netflix Eureka, Config Server
- RabbitMQ through Spring AMQP, with JSON message conversion
- Google Gemini API for recommendation generation
- Spring Data JPA with PostgreSQL, Spring Data MongoDB
- Jakarta Bean Validation, Lombok
- Maven (wrapper included in every service)

## Getting Started

### Prerequisites

- JDK 21
- Linux, macOS or WSL (the Windows `mvnw.cmd` wrapper scripts are not included)
- PostgreSQL running on `localhost:5432` with a database named `repwise_users_db`
- A MongoDB instance (local or Atlas)
- RabbitMQ on `localhost:5672`, for example with Docker:
  ```bash
  docker run -d --name rabbit -p 5672:5672 -p 15672:15672 rabbitmq:4-management
  ```
  The management UI is then at http://localhost:15672 (`guest` / `guest`).
- A Gemini API key (for the AI Service)

### Configuration

Secrets and environment-specific values are read from environment variables, never committed. Each service reads the variables it needs from its own environment, even though the property files that mention them live on the config server.

| Variable | Used by | Purpose |
|---|---|---|
| `CONFIG_SERVER_URL` | Gateway, User, Activity and AI Services | Config server address, default `http://localhost:8888` |
| `DB_USERNAME` | User Service | PostgreSQL username |
| `DB_PASSWORD` | User Service | PostgreSQL password |
| `MONGODB_URI` | Activity Service, AI Service | MongoDB connection string |
| `GEMINI_API_KEY` | AI Service | Gemini API key (required) |
| `GEMINI_API_URL` | AI Service | Gemini endpoint, defaults to the `gemini-2.5-flash` `generateContent` endpoint |
| `RABBITMQ_HOST` | Activity Service, AI Service | Broker host, default `localhost` |
| `RABBITMQ_PORT` | Activity Service, AI Service | Broker port, default `5672` |
| `RABBITMQ_USERNAME` | Activity Service, AI Service | Broker username, default `guest` |
| `RABBITMQ_PASSWORD` | Activity Service, AI Service | Broker password, default `guest` |

The queue, exchange and routing key names (`rabbitmq.queue.name`, `rabbitmq.exchange.name`, `rabbitmq.routing.key`) are defined once in the config server's shared `application.properties`.

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export MONGODB_URI="mongodb://localhost:27017/repwise"
export GEMINI_API_KEY=your_gemini_key
```

### Running

Start RabbitMQ and the config server first, then the registry, then the rest. The Gateway, User, Activity and AI services fail to start if the config server is not reachable. Each in its own terminal:

```bash
cd config-server   && ./mvnw spring-boot:run
cd eureka          && ./mvnw spring-boot:run
cd gateway         && ./mvnw spring-boot:run
cd userservice     && ./mvnw spring-boot:run
cd activityservice && ./mvnw spring-boot:run
cd aiservice       && ./mvnw spring-boot:run
```

Open the Eureka dashboard at http://localhost:8761 to confirm the services have registered. The gateway needs a short time to see newly registered services.

### Try it

All calls go through the gateway on port 8080:

```bash
# 1. Register a user and note the returned id
curl -X POST localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"secret123","firstName":"Jane","lastName":"Doe"}'

# 2. Track an activity for that user
curl -X POST localhost:8080/api/activities \
  -H "Content-Type: application/json" \
  -d '{"userId":"<id>","type":"RUNNING","duration":45,"caloriesBurned":420,"startTime":"2026-10-29T06:30:00"}'

# 3. List the user's activities
curl localhost:8080/api/activities -H "X-User-ID: <id>"

# 4. Fetch the generated recommendations (give the AI Service a few seconds)
curl localhost:8080/api/recommendations/user/<id>
```

After step 2 the AI Service log should show the saved recommendation. You can also watch `activity.queue` in the RabbitMQ management UI.

## Project Structure

```
repwise/
├── config-server/     Centralised configuration
├── eureka/            Service registry
├── gateway/           API gateway (single entry point)
├── userservice/       Users and profiles (PostgreSQL)
├── activityservice/   Workout tracking (MongoDB)
├── aiservice/         Recommendations (MongoDB, Gemini)
└── README.md
```

Each business service follows the same layered layout: `controller`, `service`, `repository`, `model`, `dto` and `config`.

## Roadmap

- [x] Centralised configuration with a config server
- [x] API gateway as a single entry point
- [x] Event-driven flow from Activity Service to AI Service via RabbitMQ
- [x] Generate AI recommendations with Gemini and save them to MongoDB
- [ ] Retry limits and a dead-letter queue for failed messages
- [ ] Avoid duplicate recommendations when a message is redelivered
- [ ] Authentication and authorisation
- [x] Centralised error handling with proper HTTP status codes
- [x] Hash passwords with BCrypt
- [x] Request validation for activities
- [ ] Containerisation with Docker Compose
- [ ] Unit and integration test coverage

## Author

Karabo Charles Matsepane
