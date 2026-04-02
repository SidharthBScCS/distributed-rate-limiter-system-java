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

## Stop the stack

```powershell
docker compose down
```

To also remove volumes:

```powershell
docker compose down -v
```
