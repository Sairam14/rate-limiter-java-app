# Rate Limiter API

A scalable, distributed rate limiter service supporting both Token Bucket and Leaky Bucket algorithms, with Redis-backed persistence, JWT authentication, and an admin UI.

---

## API Usage Examples

### Acquire Token

**POST** `/acquire?key=<user_or_api_key>`

- **Description:** Attempts to acquire a token for the given key.
- **Response:**  
  - `200 OK` — Allowed  
  - `429 TOO MANY REQUESTS` — Rate limit exceeded

**Example:**
```sh
curl -X POST "http://localhost:8080/acquire?key=testuser"
```

---

### Get Rate Limiter Status

**GET** `/status?key=<user_or_api_key>`

- **Description:** Returns the current status of the rate limiter for the key.
- **Response:** JSON with tokens left, capacity, refill rate, and algorithm type.

**Example:**
```sh
curl "http://localhost:8080/status?key=testuser"
```

---

### Admin: View All Statuses

**GET** `/admin/all-status`

- **Description:** Returns the status for all configured keys.
- **Authentication:** Requires JWT in the `Authorization` header.

**Example:**
```sh
curl -H "Authorization: Bearer <your_jwt_token>" http://localhost:8080/admin/all-status
```

---

### Admin UI

- **URL:** [http://localhost:8080/admin/](http://localhost:8080/admin/)
- **Description:** Visualizes rate limiter status and consumption.  
  (You may be prompted for a JWT token.)

---

## Architecture Design Explanation

- **Spring Boot** REST API, stateless for horizontal scalability.
- **Rate Limiting Algorithms:**  
  - **Token Bucket** and **Leaky Bucket** supported, configurable per user, API key, or globally.
- **Redis** for distributed, atomic state management using Lua scripts (ensures atomicity and high concurrency).
- **In-memory fallback:** If Redis is unavailable, the service gracefully degrades to a local in-memory rate limiter.
- **JWT Authentication:** Secures admin endpoints and UI.
- **Admin UI:** Simple HTML/JS frontend for real-time visualization.
- **Dockerized:** Service and Redis can be run together via Docker Compose.

---

## Trade-offs Made During Development

- **Atomicity vs. Simplicity:**  
  Lua scripts in Redis are used for atomic rate limiting, avoiding race conditions and supporting high concurrency. This adds complexity but is necessary for correctness at scale.
- **In-memory Fallback:**  
  Provides resilience during Redis outages, but may allow temporary over-limit usage in a multi-instance deployment until Redis is restored.
- **Stateless Service:**  
  Enables horizontal scaling, but requires all persistent state to be in Redis, making Redis a critical dependency.
- **JWT Authentication:**  
  Chosen for stateless, scalable security, but requires clients to manage tokens.
- **Simple Admin UI:**  
  A basic HTML/JS UI is provided for quick visualization. For production, a richer SPA (React/Vue) could be used.
- **No native Redis clustering/sharding logic:**  
  Relies on Redis' own clustering for scalability and high availability.

---

## Quick Start

1. **Build the project:**
   ```sh
   mvn clean package -DskipTests
   ```

2. **Run with Docker Compose:**
   ```sh
   docker-compose up --build
   ```

3. **Test the API:**
   ```sh
   curl -X POST "http://localhost:8080/acquire?key=testuser"
   curl "http://localhost:8080/status?key=testuser"
   ```

4. **Access the Admin UI:**  
   [http://localhost:8080/admin/](http://localhost:8080/admin/)

---

## Testing and Benchmarking Commands

### Run Unit Tests

```sh
mvn test
```

---

### Run Integration Tests (with Dockerized Redis via Testcontainers)

```sh
mvn verify
```
or
```sh
mvn integration-test
```

---

### Run All Tests (unit + integration)

```sh
mvn clean verify
```

---

### Run Load Tests

**Using [hey](https://github.com/rakyll/hey):**
```sh
hey -n 1000 -c 50 -m POST "http://localhost:8080/acquire?key=testuser"
```

**Using [Apache Bench (ab)]:**
```sh
ab -n 1000 -c 50 -p /dev/null -T application/json "http://localhost:8080/acquire?key=testuser"
```

---

### Benchmark the Service

**Example with wrk:**
```sh
wrk -t12 -c400 -d30s -s <(echo 'wrk.method = "POST"') "http://localhost:8080/acquire?key=testuser"
```

---

> **Note:**  
> - Ensure the service and Redis are running before load/benchmark tests.
> - Adjust `-n` (requests), `-c` (concurrency), and duration as needed for your testing environment.

---

## Prometheus Metrics

This service exposes metrics in a Prometheus-compatible format using Spring Boot Actuator and Micrometer.

### **How to View Metrics**

- **URL:**  
  [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

- **Description:**  
  This endpoint provides all application, JVM, and custom rate limiter metrics in a format that Prometheus can scrape.

### **Example Metrics Exposed**

- `ratelimiter_acquire_success` — Number of successful acquire attempts
- `ratelimiter_acquire_failed` — Number of failed acquire attempts (rate limited)
- `ratelimiter_redis_latency_seconds` — Redis operation latency (histogram/timer)
- `http_server_requests_seconds_count` — HTTP request rate
- `jvm_threads_live` — Live JVM threads (analogous to goroutines in Go)

### **Prometheus Scrape Configuration Example**

Add this to your Prometheus config:
```yaml
scrape_configs:
  - job_name: 'rate-limiter-api'
    static_configs:
      - targets: ['localhost:8080']
```

### **How to Use**

1. Start your application.
2. Visit [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) in your browser to see all metrics.
3. Point your Prometheus server to this endpoint for automated scraping and monitoring.