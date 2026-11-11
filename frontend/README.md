# MarketScope frontend

React, TypeScript, and Vite dashboard for the local futures market-data API.

## Run locally

From this directory, install dependencies and start the frontend:

```sh
npm install
npm run dev
```

In another terminal, start the Spring Boot backend from `../../backend`:

```sh
./gradlew bootRun
```

The Vite development server proxies `/api` requests to `http://localhost:8080`.
Set `VITE_API_BASE_URL` when building or running the frontend against a
different API base URL.

## Checks

```sh
npm run lint
npm test
npm run build
```

Reusable dashboard UI lives in `src/components/`, with component tests
colocated beside each component.
