# Distributed Rate Limiter System

This project is a full-stack system that helps protect an application when too many requests arrive at once.

In simple words, it works like a smart traffic controller for APIs. Instead of letting unlimited requests hit the server and slow everything down, it checks how many requests are coming in, decides what should be allowed, and blocks extra traffic when limits are crossed. This helps keep the system stable, fair, and responsive.

It also gives administrators a dashboard where they can log in, create API keys, watch traffic in real time, and understand which requests are being allowed or blocked.

## Live Demo

- Live Demo: `https://api-rate-limiter-4o3b.onrender.com`

## Tech Stack

### Backend
- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis
- JWT Authentication
- Micrometer + Prometheus

### Frontend
- React
- Vite
- React Router
- Bootstrap

### DevOps and Monitoring
- Docker
- Docker Compose
- Grafana
- Prometheus

## Features

- Admin login system using JWT-based authentication
- Create and manage API keys for different users or clients
- Distributed rate limiting with Redis
- Sliding window rate limiting algorithm
- Real-time dashboard for total, allowed, and blocked requests
- API key usage table with search and pagination
- Analytics view for traffic patterns
- Redis health check endpoint
- Monitoring support through Prometheus and Grafana
- Load testing assets using Postman, JMeter, and k6

## Screenshots

### System Architecture

![Architecture](documents/26E3179_Architecture.png)

### Dashboard UI

![Dashboard](documents/UI-IMG-1.png)

### Analytics View

![Analytics](documents/UI-IMG-4.png)

### Testing Snapshot

![Testing](documents/TEST-IMG-1.png)

## API Endpoints

These are the main endpoints in a simple, brief format.

### Authentication

- `POST /api/auth/login` - log in as admin
- `GET /api/auth/me` - get current logged-in admin
- `PUT /api/auth/me` - update admin profile
- `GET /api/auth/admins` - list admins
- `GET /api/auth/admin/{userId}` - get one admin by user ID
- `POST /api/auth/logout` - logout response endpoint

### API Key and Rate Limiting

- `GET /api` - list API keys
- `POST /api/keys` - create a new API key
- `POST /api/limit/check` - check whether a request should be allowed or blocked
- `GET /api/stats` - get overall request stats

### Dashboard and Analytics

- `GET /api/view/dashboard` - fetch dashboard cards and API key table data
- `GET /api/analytics/view` - fetch analytics chart data
- `GET /api/analytics/keys` - get API key level usage stats
- `GET /api/analytics/recent-decisions` - recent rate-limit decisions
- `GET /api/stream/dashboard` - live dashboard updates using server-sent events

### Health and Config

- `GET /api/health/redis` - check Redis connection health
- `GET /api/config` - fetch frontend UI configuration from backend
- `GET /actuator/health` - Spring health endpoint
- `GET /actuator/prometheus` - Prometheus metrics endpoint

## Future Improvements

- Add support for more rate-limiting algorithms
- Add role-based access control for multiple admin levels
- Add public cloud deployment links
- Improve API key management with edit and delete actions
- Add alerts through email or messaging tools
- Add more detailed analytics and historical trends
- Add automated CI/CD pipeline support

## Project Summary

If someone asks what this project does, the easiest answer is this:

It protects APIs from too much traffic, helps admins manage API keys, and shows everything clearly through a dashboard and monitoring tools.
