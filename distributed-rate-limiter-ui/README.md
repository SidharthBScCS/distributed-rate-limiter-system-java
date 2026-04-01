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

Example:

- `VITE_API_BASE_URL=https://distributed-rate-limiter-backend.onrender.com`

The app will call:

- `https://your-backend-domain.com/api/...`

## Notes

- `vercel.json` rewrites all frontend routes to `index.html`, so React Router paths like `/dashboard` and `/analytics` work after refresh.
- The local Vite proxy in `vite.config.js` is only for development and is not used by Vercel production builds.
