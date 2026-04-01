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

## Stop the stack

```powershell
docker compose down
```

To also remove volumes:

```powershell
docker compose down -v
```
