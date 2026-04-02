# Deployment

## Start the stack

From the repo root:

```powershell
docker compose up --build -d
```

## Services

- Backend: `http://localhost:8081`
- Spring health: `http://localhost:8081/actuator/health`
- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3002`
- Redis: `localhost:6380`
- Postgres: `localhost:5433`

## Default credentials

- Backend admin login: `admin` / `admin`
- Grafana: `admin` / `admin`
- Postgres: `postgres` / `postgres`

## Authentication notes

- If you run the React app separately with Vite, the dev proxy defaults to `http://localhost:8081` to match the Docker backend port published by this stack.
- If your frontend and backend are on different origins in production, set `CORS_ALLOWED_ORIGINS` to the frontend URL and configure the backend session cookie with `SESSION_COOKIE_SECURE=true` and `SESSION_COOKIE_SAME_SITE=None`.

## Cloud PostgreSQL

- The backend can use either `DB_URL` or a standard provider `DATABASE_URL`.
- `DB_URL` must be a JDBC URL, for example: `jdbc:postgresql://host:5432/ratelimiter_db?sslmode=require`
- `DATABASE_URL` can be a provider URL, for example: `postgresql://user:password@host:5432/ratelimiter_db?sslmode=require`
- If both are set, `DB_URL` wins.

Example backend environment variables:

```powershell
$env:DATABASE_URL="postgresql://user:password@host:5432/ratelimiter_db?sslmode=require"
$env:DB_USERNAME="user"
$env:DB_PASSWORD="password"
```

Or with JDBC directly:

```powershell
$env:DB_URL="jdbc:postgresql://host:5432/ratelimiter_db?sslmode=require"
$env:DB_USERNAME="user"
$env:DB_PASSWORD="password"
```

## Stop the stack

```powershell
docker compose down
```

To also remove volumes:

```powershell
docker compose down -v
```
