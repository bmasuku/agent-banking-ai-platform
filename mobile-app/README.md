# Agent Banking — Mobile (Expo)

React Native Expo app for **AGENT** and **AREA_MANAGER** roles. Role is taken from the JWT/`/auth/me` response and cannot be switched in the UI.

## Stack

Expo · React Native · TypeScript · React Navigation · TanStack Query · Axios · Zustand · Expo Location · Expo Notifications · react-native-maps · gifted-charts

## Run

```bash
# API (default mobile base URL)
# Backend: http://localhost:18080/api/v1
# Postgres demo: docker run … -p 55432:5432 … (see project scripts)

cd mobile-app
npm install
npx expo start
```

Override API URL when needed:

```bash
EXPO_PUBLIC_API_URL=http://<lan-ip>:18080/api/v1 npx expo start
```

Demo logins (password `Demo@2026!`): `agent1`, `manager`.

## Notes

- All balances and metrics come from Spring Boot APIs (no hardcoded dashboard stats).
- Field visit check-ins are stored on-device only until a backend visit API exists.
- Location is foreground-only; Harare CBD coordinates are used if permission is denied.
