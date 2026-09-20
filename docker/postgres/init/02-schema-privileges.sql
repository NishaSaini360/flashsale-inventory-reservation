-- Postgres 15+ already restricts CREATE on the public schema, but USAGE
-- is still open to PUBLIC. Tighten each database to its owner only.

\connect inventory_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL  ON SCHEMA public TO inv_user;

\connect reservation_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL  ON SCHEMA public TO res_user;

\connect order_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL  ON SCHEMA public TO ord_user;
