# Bassance

Bassance is a Spring Boot application with a static web frontend, PostgreSQL persistence, and a Python audio-classification service. The frontend is served by Spring, so the web app and API deploy as one service.

## Production layout

- **Voroa:** deploy the repository as a Docker web app using the root `Dockerfile`. It serves both the frontend and API. Voroa provides `PORT=3000`; Spring reads that value. Do not deploy the static frontend as a separate service.
- **Neon:** managed PostgreSQL database. Use Neon’s pooled connection string only if its JDBC format is compatible; otherwise use the direct host and keep the connection limit small. Include `sslmode=require` in `DB_URL`.
- **Azure Container Apps:** deploy the ML service from `src/main/java/com/example/edm_app/ml_service`. It needs a public HTTPS endpoint because Voroa calls it across providers. Prediction requests require the shared `ML_SERVICE_TOKEN`; `/health` is an unauthenticated health check.

Deploy in that order: create the Neon database, build and deploy the ML container, verify its HTTPS `/health`, then configure and deploy the Voroa app. Flyway applies schema migrations at startup; Hibernate validates the schema rather than altering it.

## Voroa environment variables

Enter these in Voroa’s environment-variable/secret settings, not in a deployed `.env` file. Generate fresh production secrets; do not reuse development values or the local test token.

| Variable | Production value |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://<neon-host>/<database>?sslmode=require` |
| `DB_USERNAME` | Neon database role (often `neondb_owner`; use the role shown by Neon) |
| `DB_PASSWORD` | The corresponding Neon password; keep it secret |
| `JWT_SECRET` | A fresh, cryptographically random secret of at least 32 characters |
| `JWT_EXPIRY_MS` | `3600000` (one hour; adjust only intentionally) |
| `ML_SERVICE_URL` | Azure Container App HTTPS origin, e.g. `https://<app>.<region>.azurecontainerapps.io` (no `/predict` suffix) |
| `ML_SERVICE_TOKEN` | A fresh random secret, at least 32 characters, identical to the ML service’s value |
| `COOKIE_SECURE` | `true` |
| `MAX_UPLOAD_BYTES` | `52428800` (50 MiB) |
| `DB_POOL_SIZE` | `3` to start; lower if Neon connection limits require it |
| `DB_POOL_MIN_IDLE` | `0` |
| `DB_POOL_IDLE_TIMEOUT_MS` | `30000` |

Voroa supplies `PORT`; do not override it with a different port. Set the database and signing secrets through the provider’s protected settings. Keep the ML token out of image build arguments, source control, and logs.

## Azure ML service

Build with the ML-service directory as the Docker build context (the model file is included in that context):

```sh
docker build -t <registry>/<namespace>/bassance-ml:latest src/main/java/com/example/edm_app/ml_service
docker push <registry>/<namespace>/bassance-ml:latest
```

Create an Azure Container App from that image with:

- Ingress enabled, **external**, target port `8001`, HTTPS URL.
- `PORT=8001` and `ML_SERVICE_TOKEN` configured as an app secret and referenced as an environment variable.
- Health probe path `/health` on port `8001`.
- One worker (the image starts Uvicorn with one worker); begin with at least 2 GiB memory and benchmark with real audio before reducing it.
- For cost-conscious use, minimum replicas `0` and maximum replicas `1`. Scale-to-zero can make the first prediction slow while the model starts; monitor actual Azure credit/consumption and configure any available spending alerts.

The ML endpoint is publicly reachable, so keep the token random and private. The token protects prediction requests, not the health endpoint or network access to the service. Do not put the model token in the Docker image.

## Voroa deployment

Connect the GitHub repository in Voroa, select its Docker deployment option and root `Dockerfile`, and configure the variables above. Confirm the service uses the platform-provided `PORT=3000`. Once deployed, verify `/actuator/health`, `/login.html`, registration/login, and a complete audio upload and result fetch.

The Docker build runs Maven `package` (including tests), creates a non-root runtime image, and sets a conservative JVM memory ceiling. The model has its own Dockerfile; it is not built into the Spring image.

## Local development

1. Copy `.env.example` to `.env` and set local PostgreSQL credentials plus separate random values for `JWT_SECRET` and `ML_SERVICE_TOKEN`. `.env` is ignored by Git.
2. Start PostgreSQL and install FFmpeg so it is available on `PATH`.
3. In `src/main/java/com/example/edm_app/ml_service`, create a Python 3.11 virtual environment, install CPU PyTorch/torchvision/torchaudio from the PyTorch CPU index, then install `requirements.txt`. Set `ML_SERVICE_TOKEN` and start `uvicorn app:app --host 127.0.0.1 --port 8001`.
4. From the repository root, start the Spring app with Java 17 and Maven (`mvn spring-boot:run`). It reads `.env` from the working directory.

Run Java tests with `mvn test`. For a local end-to-end smoke test, register a disposable account, log in, upload a small MP3, verify the returned classification, and remove the test account and uploaded temporary file afterward.

## Before launch

- Confirm the production database is active, has sufficient storage/compute, and is backed up as appropriate. Neon’s free tier has usage/storage limits and may suspend idle compute; avoid artificial keepalive traffic.
- Confirm Voroa and Azure can reach the public services and that browser traffic uses HTTPS (`COOKIE_SECURE=true`).
- Check provider logs and limits after a real upload, including memory use, cold-start duration, database connections, and uploaded audio cleanup.
- `.env.example` is a local template only. Configure production secrets in each provider’s secret manager; never commit `.env`.

The repository also contains `render.yaml` for a legacy deployment path; it is not part of the Voroa + Neon + Azure layout above.
