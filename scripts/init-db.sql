-- One Postgres instance, one schema per service. Each service's Flyway migrations
-- own the tables inside its own schema; there are no cross-schema foreign keys or
-- joins, so the schemas stay independently replaceable by real separate databases.

CREATE SCHEMA IF NOT EXISTS menu     AUTHORIZATION rto;
CREATE SCHEMA IF NOT EXISTS orders   AUTHORIZATION rto;
CREATE SCHEMA IF NOT EXISTS kitchen  AUTHORIZATION rto;
CREATE SCHEMA IF NOT EXISTS tables   AUTHORIZATION rto;
CREATE SCHEMA IF NOT EXISTS payment  AUTHORIZATION rto;
