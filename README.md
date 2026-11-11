# Futures Market Data Quality and Analytics

A locally runnable market-data application with a Spring Boot/Kotlin backend
and a React/Vite frontend. The backend imports CSV and Parquet datasets,
validates records, reports quality findings, and provides daily OHLCV and
rolling 15-minute bar-based VWAP analytics.

## Requirements

- Java 21
- Node.js 26 and npm

## Run locally

Start the backend:

```sh
cd backend
./gradlew bootRun
```

In another terminal, start the frontend:

```sh
cd frontend/vite-project
npm install
npm run dev
```

The backend API is available at `http://localhost:8080`; Vite serves the
frontend at the address printed by its dev server. The dashboard proxies its
API requests through Vite to the local backend. It supports CSV and Parquet
imports, dataset selection, contract/date filters, daily OHLCV and rolling
VWAP charts, quality findings, and rule-based insights.

## Import a dataset

Uploads require the file and its cadence as multipart fields. Use `minute` for
one bar per minute or `daily` for one bar per calendar day:

```sh
curl -X POST http://localhost:8080/datasets \
  -F 'file=@/path/to/bars.csv' \
  -F 'frequency=minute'
```

CSV and Parquet files must contain contract, timestamp, open, high, low, close,
and volume fields. A successful response includes the dataset ID, import
summary, and declared frequency. The ID is required for subsequent quality,
insights, and analytics requests.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/datasets` | Import a CSV or Parquet file (`file`, `frequency`) |
| `GET` | `/datasets` | List imported datasets |
| `GET` | `/datasets/{id}/quality?page=0&size=100` | Page through quality findings |
| `GET` | `/insights?datasetId={id}` | Get rule-based insights |
| `GET` | `/analytics/daily-bars?datasetId={id}&from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=100` | Get paged daily OHLCV bars |
| `GET` | `/analytics/vwap?datasetId={id}&from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=100` | Get paged rolling VWAP points |

Analytics pagination defaults to page `0`, size `100`, and allows sizes up to
`500`. Responses include `totalElements`, `page`, `size`, and `items`. Quality
pagination uses the same defaults and maximum.

For example, after importing a dataset:

```sh
curl 'http://localhost:8080/analytics/daily-bars?datasetId=DATASET_ID&from=2025-01-01&to=2025-01-31&page=0&size=100'
```

## Data and quality behavior

- Uploads are limited to 50 MB.
- A `minute` dataset is expected to contain a bar every minute; a `daily`
  dataset is expected to contain one for every calendar day, including
  weekends and holidays.
- Missing spans are reported as quality findings but are not counted as
  rejected input rows. Exact duplicate bars and conflicting bars for the same
  contract and timestamp are reported separately.
- Analytics are scoped to one dataset ID and can additionally be filtered by
  contract and date range.
- VWAP is an OHLCV-based approximation: typical price `(high + low + close) / 3`
  weighted by bar volume.
- The repository is in-memory. Imported data is cleared when the backend stops.

Minute-level source timestamps in the provided sample use Chicago wall-clock
labels. See [ARCHITECTURE.md](./ARCHITECTURE.md) for design boundaries,
timestamp semantics, and current limitations.

## Tests

Run the backend unit and integration test suites:

```sh
cd backend
./gradlew test
```
