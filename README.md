# Repwise

> Track every rep. Train smarter.

Repwise is a fitness tracking platform built as a set of independent Spring Boot microservices. Services discover each other through a Eureka registry, own their own data stores, and communicate over load-balanced HTTP and asynchronous RabbitMQ messages.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.4-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-users-336791)
![MongoDB](https://img.shields.io/badge/MongoDB-activities%20%26%20recommendations-47A248)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-messaging-FF6600)

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
                                  |                         ^   |
                                  |   RabbitMQ              |   | HTTPS
                                  |   activity.exchange     |   v
                                  +--> activity.queue ------+  Gemini API
```

When an activity is saved, the Activity Service publishes it to RabbitMQ and returns immediately. The AI Service consumes it from the queue and asks Gemini for a personalised recommendation.

Each service is a self-contained Maven project with its own wrapper, build, configuration and database. There is deliberately no shared parent POM or shared library, so any service can be built, tested, deployed and scaled on its own.

## Services

| Service | Directory | Port | Datastore | Status |
|---|---|---|---|---|
| Eureka Server | `eureka` | 8761 | none | Working |
| User Service | `userservice` | 8000 | PostgreSQL | Working |
| Activity Service | `activityservice` | 8001 | MongoDB | Working |
| AI Service | `aiservice` | 8002 | MongoDB | In progress |
| RabbitMQ (broker) | external | 5672 (AMQP), 15672 (management UI) | none | Required |

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

After saving, it publishes the activity to RabbitMQ (exchange `activity.exchange`, routing key `activity.tracking`) as JSON. A failure to publish is logged but does not fail the request, so the activity is still saved.

Supported activity types: `RUNNING`, `SWIMMING`, `WALKING`, `CYCLING`, `WEIGHT_LIFTING`, `PUNCHING_BAG`, `ROPE_SKIPPING`, `AEROBICS`.

### AI Service
Serves personalised recommendations for logged activities. The data model (improvements, suggestions and safety guidance per activity), the MongoDB repository, and endpoints to fetch recommendations by user or by activity are in place.

It listens on `activity.queue` for activities published by the Activity Service, builds a prompt from each one and calls the Gemini API through `WebClient`. The reply is parsed into a `Recommendation`. For now the recommendation is only logged: it is not yet saved to MongoDB, so the endpoints above return nothing for new activities. A message whose processing fails is requeued by default, and retry limits and a dead-letter queue are not configured yet.

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
- RabbitMQ through Spring AMQP, with JSON message conversion
- Google Gemini API for recommendation generation
- Spring Data JPA with PostgreSQL, Spring Data MongoDB
- Jakarta Bean Validation, Lombok
- Maven (wrapper included in every service)

## Getting Started

### Prerequisites

- JDK 21
- PostgreSQL running on `localhost:5432` with a database named `repwise_users_db`
- A MongoDB instance (local or Atlas)
- RabbitMQ on `localhost:5672`, for example with Docker:
  ```bash
  docker run -d --name rabbit -p 5672:5672 -p 15672:15672 rabbitmq:4-management
  ```
  The management UI is then at http://localhost:15672 (`guest` / `guest`).
- A Gemini API key (for the AI Service)

### Configuration

Secrets are read from environment variables, never committed.

| Variable | Used by | Purpose |
|---|---|---|
| `DB_USERNAME` | User Service | PostgreSQL username |
| `DB_PASSWORD` | User Service | PostgreSQL password |
| `MONGODB_URI` | Activity Service, AI Service | MongoDB connection string |
| `GEMINI_API_KEY` | AI Service | Gemini API key (required) |
| `GEMINI_API_URL` | AI Service | Gemini endpoint, defaults to `gemini-2.5-flash` `generateContent` |
| `RABBITMQ_HOST` | Activity Service, AI Service | Broker host, default `localhost` |
| `RABBITMQ_PORT` | Activity Service, AI Service | Broker port, default `5672` |
| `RABBITMQ_USERNAME` | Activity Service, AI Service | Broker username, default `guest` |
| `RABBITMQ_PASSWORD` | Activity Service, AI Service | Broker password, default `guest` |

The queue, exchange and routing key names (`rabbitmq.queue.name`, `rabbitmq.exchange.name`, `rabbitmq.routing.key`) are set in each service's `application.properties` and must be identical in the Activity Service and the AI Service.

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export MONGODB_URI="mongodb://localhost:27017/repwise"
export GEMINI_API_KEY=your_gemini_key
```

### Running

Start RabbitMQ and the registry first, then the rest in any order. Each in its own terminal:

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

After step 2, the AI Service log should show the generated recommendation. You can also watch `activity.queue` in the RabbitMQ management UI.

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

- [x] Event-driven flow from Activity Service to AI Service via RabbitMQ
- [x] Generate AI recommendations with Gemini for logged activities (currently logged only)
- [ ] Save generated recommendations to MongoDB so the AI Service endpoints return them
- [ ] Retry limits and a dead-letter queue for failed messages
- [ ] API gateway as a single entry point
- [ ] Authentication and authorisation
- [ ] Centralised error handling with proper HTTP status codes
- [ ] Containerisation with Docker Compose
- [ ] Unit and integration test coverage

## Author

Karabo Charles Matsepane
