-- Mirrors docs/runbooks/database-bootstrap.md. Kept in step with it: a privilege the tests grant
-- but the runbook does not is a failure that only shows up after a deploy.
CREATE ROLE migrator LOGIN PASSWORD 'migrator';
CREATE ROLE app      LOGIN PASSWORD 'app';

GRANT migrator TO jugueria_admin;

REVOKE ALL ON DATABASE jugueria FROM PUBLIC;
GRANT CONNECT ON DATABASE jugueria TO app, migrator;
GRANT CREATE  ON DATABASE jugueria TO migrator;

ALTER DEFAULT PRIVILEGES FOR ROLE migrator GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO app;
ALTER DEFAULT PRIVILEGES FOR ROLE migrator GRANT USAGE, SELECT                  ON SEQUENCES TO app;
