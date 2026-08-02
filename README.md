# EDM App (Track Genre Classifier)

This project is a full-stack EDM track classifier:
- **Backend:** Spring Boot (Java 17), Spring Security (JWT), Spring Data JPA
- **Database:** PostgreSQL
- **ML service:** FastAPI + PyTorch model for genre prediction
- **Frontend:** Static HTML pages (`login.html`, `register.html`, `dashboard.html`)

## Features

- User registration and login
- JWT-based authenticated APIs
- Upload audio tracks for genre prediction
- Save and view prediction history per user
- Rename uploaded tracks from dashboard

## Project Structure

```text
src/main/java/com/example/edm_app
  ├─ controller      # Auth + track APIs
  ├─ service         # Business logic
  ├─ repository      # JPA repositories
  ├─ entity          # JPA entities
  ├─ jwt             # JWT utility + filter
  ├─ configuration   # Security config
  └─ ml_service      # Python FastAPI model server
src/main/resources/static
  ├─ login.html
  ├─ register.html
  └─ dashboard.html
```

## Prerequisites

- Java 17
- Maven (or use `mvnw.cmd`)
- PostgreSQL running locally
- Python 3.10+ (recommended) for ML service

## Configuration

The app reads DB config from environment variables (with local defaults):

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/edm_app}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
```

Create the database before running:

```sql
CREATE DATABASE edm_app;
```

On Windows PowerShell, you can set credentials like:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_password"
```

## Run the ML Service

From:

`src\main\java\com\example\edm_app\ml_service`

Install dependencies and start server:

```powershell
pip install -r requirements.txt
uvicorn app:app --host 127.0.0.1 --port 8001
```

The Java backend calls this endpoint:

`POST http://127.0.0.1:8001/predict`

## Run the Spring Boot App

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

or

```powershell
mvn spring-boot:run
```

## Use the App

Open in browser:

`http://localhost:8080/login.html`

Then:
1. Register a new user
2. Login
3. Upload an audio file from dashboard
4. View predicted genres and rename tracks

## API Endpoints

- `POST /auth/register`
- `POST /auth/login`
- `POST /tracks/upload` (JWT required)
- `GET /tracks` (JWT required)
- `PUT /tracks/{id}/rename` (JWT required)

## Notes

- FFmpeg is not directly required by this codebase, but non-WAV formats may depend on your local `torchaudio` backend support.
- JWT signing key is generated at runtime in current implementation, so tokens become invalid after backend restart.
