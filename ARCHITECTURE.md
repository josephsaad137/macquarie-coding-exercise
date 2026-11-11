# Architecture

## Current system

The project is a local modular application with a React/Vite frontend and a
Spring Boot/Kotlin REST API.

```text
Browser
  React + TypeScript + Vite (localhost:5173, dashboard and typed API client)
       │
       │ /api requests (Vite development proxy)
       ▼
  Spring Boot + Kotlin REST API (localhost:8080)
       │
       ├── controllers and response DTOs
       ├── dataset ingestion, quality, analytics, insights services
       └── DatasetRepository
              └── InMemoryDatasetRepository
```

The repository is the storage boundary used by the services. Its current
implementation stores data in memory, so imported datasets are lost when the
backend stops.

## Backend components and responsibilities

- **API:** `DatasetController`, `AnalyticsController`, and
  `InsightsController` validate HTTP inputs and map service models to response
  DTOs.
- **Services:** `DatasetService` validates imported records and coordinates
  ingestion; `CsvFileReader` uses Apache Commons CSV, while `ParquetFileReader`
  uses Apache Parquet/Avro. Both readers stream records into the same
  validation and quality logic;
  `QualityService` pages quality findings; `AnalyticsService` calculates daily
  bars and rolling VWAP; `InsightsService` generates deterministic
  recommendations.
- **Persistence:** `DatasetRepository` stores and retrieves dataset summaries,
  normalized bars, and quality findings. `InMemoryDatasetRepository` provides
  synchronized, per-dataset storage and immutable list snapshots.
- **Models:** backend service models represent dataset metadata, normalized
  OHLCV bars, quality findings, analytics results, and insights.

## Import and quality flow

1. `POST /datasets` accepts a CSV or Parquet multipart upload, a required
   `frequency` (`minute` or `daily`), and a maximum file size of 50 MB.
2. The importer resolves common source column names to contract, timestamp,
   open, high, low, close, and volume. Contracts are normalized to uppercase.
   Invalid rows are rejected with structured quality findings; accepted bars
   are retained.
3. Duplicate records for the same contract and timestamp are classified as
   `DUPLICATE_BAR` when all bar values match, or `CONFLICTING_BAR` when values
   differ. The first valid record is kept; subsequent records are rejected.
4. Gap checks use the consumer-declared frequency independently for each
   contract. Minute datasets expect one bar each minute; daily datasets expect
   one bar every calendar day, including weekends and holidays. Each continuous
   missing span creates one `MISSING_TIMESTAMP` finding with its first missing
   timestamp and missing-bar count. Gap findings do not increase
   `rowsRejected`, but do cause `COMPLETED_WITH_WARNINGS`.
5. The summary, accepted bars, and quality findings are saved together through
   `DatasetRepository`.

Quality findings include malformed records, invalid OHLC values, invalid
volume, exact duplicates, conflicting bars, and missing timestamps. Findings
are paginated by source row, with synthetic gap findings (which have no source
row) after row-associated findings.

## Timestamp and analytics semantics

- Sample minute-level `timestamp_chicago_wall` values are timezone-naive
  Chicago wall-clock labels, not UTC instants. Daily sample `date` values are
  calendar dates. The importer also accepts ISO timestamps, offsets, and
  integer epoch-millisecond timestamps. Parquet nanosecond values for
  `timestamp_chicago_wall` are converted to local date-time components without
  applying a timezone offset.
- Daily OHLCV groups bars by contract and calendar date. It uses the first open,
  maximum high, minimum low, last close in timestamp order, and summed volume.
- Rolling 15-minute VWAP is an OHLCV approximation: typical price
  `(high + low + close) / 3`, weighted by volume, over `(end - 15 minutes, end]`.
  Results are emitted at bar timestamps when the window has positive volume.
- Both analytics endpoints require a dataset ID and only process that dataset's
  bars. Optional contract and inclusive date-range filters are supported.
- Analytics and quality endpoints use page-based pagination, defaulting to page
  `0` and size `100`, with a maximum size of `500`. Paginated responses include
  `totalElements`, `page`, `size`, and `items`.
- Insights are rule-based. A quality code appearing at least twice gets a
  recurring insight; other findings are summarized in a general quality
  insight.

## HTTP API

Responses are JSON. Uploads use multipart form data.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/datasets` | Import `file` and required `frequency=minute\|daily` |
| `GET` | `/datasets` | List dataset summaries and declared frequencies |
| `GET` | `/datasets/{id}/quality?page=0&size=100` | Page quality findings |
| `GET` | `/insights?datasetId={id}` | Get rule-based insights for one dataset |
| `GET` | `/analytics/daily-bars?datasetId={id}&from={date}&to={date}&page=0&size=100` | Get paged daily OHLCV bars |
| `GET` | `/analytics/vwap?datasetId={id}&from={date}&to={date}&page=0&size=100` | Get paged rolling VWAP points |

Unknown dataset IDs return `404`; invalid dates, pagination, or frequency
parameters return `400`.

## Frontend and local development

The React dashboard uses a typed API client for dataset imports, selection,
quality findings, insights, daily OHLCV bars, and rolling VWAP. Users can filter
analytics by contract and inclusive date range. In development, Vite proxies
`/api` to the backend at `http://localhost:8080`, avoiding browser CORS
configuration for the local workflow.

Start the backend and frontend separately:

```sh
cd backend && ./gradlew bootRun
cd frontend/vite-project && npm run dev
```

See [README.md](./README.md) for runtime requirements, API examples, and test
commands.

## Verification

Backend unit tests cover parsing, quality rules, services, and calculations.
The Spring integration test exercises dataset import through the API and checks
that quality, insights, and analytics read the saved repository data. Run all
backend tests with:

```sh
cd backend && ./gradlew test
```

## Current limitations

- Repository data is in-memory and does not survive a backend restart.
- The frontend dashboard and frontend-to-backend communication are not
  implemented.
- Gap checking follows the declared cadence literally; it does not account for
  exchange sessions, holidays, or periods when no bar was expected.
- VWAP is a bar-based approximation, not trade-level VWAP.
- Analytics currently load the selected dataset's bars in memory before
  aggregating and paging the result.
