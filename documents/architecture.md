# PTReport Backend Architecture

## Overview
The `PTReport` service is a Spring Boot microservice responsible for processing JTL (JMeter) results files and generating performance report summaries in XLS format.

## Technology Stack
- **Runtime**: Java 17
- **Framework**: Spring Boot 3.x
- **Libraries**:
  - Apache POI (Excel generation)
  - OpenCSV (CSV/JTL parsing)
  - Lombok
- **Logging**: SLF4j with Log4j2
- **Architecture Pattern**: Controller -> Service -> ServiceImpl

## Security & Observability
- Integrates with standard observability stack (Prometheus, Loki, Tempo) toggled via `application.yml` profiles.
- Supports cross-origin requests (CORS) from `PTReportUI`.

## API Endpoints
- `POST /api/v1/reports/generate`
  - Accepts `file` (MultipartFile .jtl), `runId` (String), `testType` (String)
  - Returns: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` stream.
