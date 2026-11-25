-- Optional extensions used by the platform. Schema objects are created by Flyway
-- when the Spring Boot backend starts (classpath:db/migration V1–V5).
CREATE EXTENSION IF NOT EXISTS pgcrypto;
