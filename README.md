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

## System Architecture

![System Architecture](documents/26E3179_Architecture.png)

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
