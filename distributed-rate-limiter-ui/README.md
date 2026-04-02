# Frontend Deployment

This frontend is ready to deploy on Vercel.

## Vercel settings

- Framework preset: `Vite`
- Root directory: `distributed-rate-limiter-ui`
- Build command: `npm run build`
- Output directory: `dist`

## Environment variable

Set this in Vercel Project Settings:

- `VITE_API_BASE_URL=https://your-backend-domain.com`
- `VITE_API_BASE_URL` must point at the backend origin that sets the login session cookie.

Example:

- `VITE_API_BASE_URL=https://distributed-rate-limiter-backend.onrender.com`

The app will call:

- `https://your-backend-domain.com/api/...`

## Notes

- `vercel.json` rewrites all frontend routes to `index.html`, so React Router paths like `/dashboard` and `/analytics` work after refresh.
- The local Vite proxy in `vite.config.js` is only for development and is not used by Vercel production builds.
- For cross-site auth in production, the backend must allow the frontend origin in `CORS_ALLOWED_ORIGINS`, and the session cookie should usually be configured with `SESSION_COOKIE_SECURE=true` and `SESSION_COOKIE_SAME_SITE=None`.
