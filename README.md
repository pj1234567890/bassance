# EDM Track Classifier

Full-stack audio genre classifier: Spring Boot (Java 17), JWT authentication, PostgreSQL, and a FastAPI/PyTorch inference service.

## Run locally

Prerequisites: Java 17, Maven, Python 3.11, PostgreSQL 17, and FFmpeg.

1. Create a PostgreSQL database named `edm_app`.
2. Copy `.env.example` to `.env`, then set database credentials and generate independent random values for `JWT_SECRET` and `ML_SERVICE_TOKEN` (at least 32 characters each). Keep `.env` private; it is ignored by Git.
3. In the ML service directory, install the CPU PyTorch build and Python dependencies:

   ```powershell
   pip install --index-url https://download.pytorch.org/whl/cpu torch==2.9.1 torchvision==0.24.1 torchaudio==2.9.1
   pip install -r requirements.txt
   $env:ML_SERVICE_TOKEN = "the-same-value-as-ML_SERVICE_TOKEN-in-.env"
   uvicorn app:app --host 127.0.0.1 --port 8001
   ```

4. In a second terminal, start Spring Boot from the repository root:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

5. Open `http://localhost:8080/login.html`. Register, sign in, then upload an MP3, WAV, FLAC, OGG, or M4A file (up to 50 MB).

For local HTTP only, set `COOKIE_SECURE=false` in `.env`. Production must use secure cookies over HTTPS.

## Deploy to Render

The repository includes a root `Dockerfile`, an ML-service Dockerfile, and `render.yaml` for a multi-service Render Blueprint. Connect the GitHub repository to Render and create a Blueprint from `render.yaml`. The Blueprint provisions:

- A public Spring Boot web service (the only service exposed to the internet).
- A private FastAPI/PyTorch service, with the model checkpoint included in its image.
- Managed PostgreSQL, with its public IP allowlist disabled.

The services and database are configured for Singapore; keep them in the same region for private networking. Render generates the JWT and ML-service secrets. The backend talks to the ML service using its private hostname, and database settings are injected from the managed database. Use paid plans for reliable operation; the ML service is configured for 2 CPU / 4 GB RAM as a starting point. Benchmark memory and inference latency, then right-size it. Do not enable free Postgres for production data.

Database changes are applied by Flyway migrations and Hibernate validates the resulting schema. The Render Blueprint creates a fresh database. If migrating an existing database, take a verified backup and explicitly baseline it only after confirming its schema matches the migration; do not enable automatic baseline on an unknown schema.

## Security controls

- Browser JWTs are `HttpOnly`, `Secure` in production, and `SameSite=Strict`; they are not stored in browser storage. Unsafe requests require a CSRF token.
- Public login/registration and authenticated uploads have in-process rate limits. Uploads are capped, signature-checked, and restricted to supported audio formats. The ML service independently authenticates the backend, streams uploads to temporary storage, limits inference concurrency, checks audio metadata/duration, and removes temporary files.
- Do not expose the ML service or database publicly. Store secrets in Render’s environment/secret settings, never in source control.
- The API emits a restrictive Content Security Policy and standard browser security headers. Passwords remain BCrypt-hashed.

The rate limiter is per application instance. Keep the API at one instance unless/until the limiter is moved to a shared store such as Redis; otherwise limits multiply across replicas.
