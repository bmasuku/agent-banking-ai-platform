# AI FloatShield — Web Portal

React + Vite staff portal for **RISK_OFFICER**, **FINANCE_OFFICER**, and **ADMIN**.

## Stack

React · TypeScript · Vite · Tailwind CSS · Shadcn-style UI · React Router · TanStack Query · Axios · Recharts · Zustand · Vitest

## Run

```bash
# Backend should be on http://localhost:18080 (Vite proxies /api)
cd web-app
npm install
npm run dev
```

Open http://localhost:5173

Demo accounts (password `Demo@2026!`): `risk`, `finance`, `admin`.

## Scripts

- `npm run dev` — development server
- `npm run build` — typecheck + production build
- `npm test` — Vitest unit tests

## Notes

- Role-based navigation and protected routes; role comes from JWT / `/auth/me`.
- All dashboard metrics and workflows call Spring Boot APIs (no static demo numbers).
- Override API with `VITE_API_URL=http://host:port/api/v1`.
