# Hanzi Cardgen App

Local web app for inspecting `dictionary/dict.sqlite3`, authoring flashcards,
and eventually syncing cards and media into Anki.

The app is split into:

- `backend`: Java HTTP API server backed by SQLite.
- `frontend`: React + TypeScript UI built with Vite.

The generated dictionary lives in `dictionary/dict.sqlite3`. User-owned app
state, such as accounts, sessions, and component meanings, lives in PostgreSQL.

## Development

For quick local testing, use the dev launcher:

```bash
app/scripts/dev.sh
```

It starts PostgreSQL if needed, creates the `hanzi_cardgen` app database if it
does not exist, builds and starts the backend, and starts the Vite frontend.
The app will be available at `http://127.0.0.1:5173`.

Useful overrides:

```bash
APP_DB_NAME=hanzi_cardgen_test BACKEND_PORT=9876 FRONTEND_PORT=5174 app/scripts/dev.sh
```

If PostgreSQL is installed somewhere non-standard, set `POSTGRES_DATA_DIR`.

### App Database

Authentication and per-user preferences require a PostgreSQL database. The
backend initializes the schema from `backend/src/main/resources/db/app.sql`
when an app database URL is configured.

Create a local database:

```bash
createdb hanzi_cardgen
```

Then provide the connection when starting the backend:

```bash
export HANZI_APP_DB_URL="jdbc:postgresql://localhost:5432/hanzi_cardgen"
export HANZI_APP_DB_USER="$USER"
export HANZI_APP_DB_PASSWORD=""
```

If your local PostgreSQL accepts peer/trust auth, `HANZI_APP_DB_USER` and
`HANZI_APP_DB_PASSWORD` can be omitted:

```bash
export HANZI_APP_DB_URL="jdbc:postgresql://localhost:5432/hanzi_cardgen"
```

The backend also accepts command-line flags:

```bash
java -jar target/hanzi-cardgen-backend-1.0.0.jar \
  --app-db-url jdbc:postgresql://localhost:5432/hanzi_cardgen \
  --app-db-user "$USER" \
  --app-db-password ""
```

Supported environment variables:

- `HANZI_APP_DB_URL` or `DATABASE_URL`
- `HANZI_APP_DB_USER` or `PGUSER`
- `HANZI_APP_DB_PASSWORD` or `PGPASSWORD`

Without an app database URL, dictionary lookup still works, but auth endpoints
return an authentication database configuration error.

#### Reseting the Database
```bash
dropdb -h 127.0.0.1 -p 5432 hanzi_cardgen
createdb -h 127.0.0.1 -p 5432 hanzi_cardgen
```

### Backend

The backend targets Java 25. Make sure Maven is also using JDK 25:

```bash
java -version
mvn -version
```

Run the backend:

Optionally, clean old jars from target
```bash
mvn clean package
```

```bash
cd app/backend
mvn package
java -jar target/hanzi-cardgen-backend-1.0.0.jar
```

The backend defaults to `http://127.0.0.1:8766` so it does not collide with
AnkiConnect, which commonly listens on `127.0.0.1:8765`. To use another port:

```bash
java -jar target/hanzi-cardgen-backend-1.0.0.jar --port 8770
```

In another terminal, run the frontend dev server:

```bash
cd app/frontend
npm install
npm run dev
```

Then open:

```text
http://127.0.0.1:5173
```

Vite proxies `/api` requests to the Java server at `http://127.0.0.1:8766`.

## Production-style Run

Build the frontend first:

```bash
cd app/frontend
npm install
npm run build
```

Then build and start the Java server:

```bash
cd ../backend
mvn package
java -jar target/hanzi-cardgen-backend-1.0.0.jar
```

Open:

```text
http://127.0.0.1:8766
```

## API

- `GET /api/search?q=漢&hsk=1&stroke_min=1&stroke_max=20`
- `GET /api/glyph/漢`
- `GET /api/glyph/U+6F22`
- `GET /api/metadata`
- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/sign-in`
- `POST /api/auth/sign-out`
- `GET /api/auth/session`

The backend also exposes scaffolded service routes for future implementation:

- `/api/cards/*`
- `/api/mnemonics/*`
- `/api/anki/*`
- `/api/preferences/*`

Those routes currently return `501` with a structured service stub payload.

The entry endpoint returns the raw glyph row, a recursive IDS decomposition
tree, definition-bearing component rows, compact leaf component rows, and
characters that directly reuse the selected glyph as an IDS component.
