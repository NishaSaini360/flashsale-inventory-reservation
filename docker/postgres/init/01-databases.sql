-- Runs once, on first boot of an empty data directory.
-- Three databases, three owners: each service can only reach its own data.

CREATE USER inv_user WITH PASSWORD 'inv_pass';
CREATE USER res_user WITH PASSWORD 'res_pass';
CREATE USER ord_user WITH PASSWORD 'ord_pass';

CREATE DATABASE inventory_db   OWNER inv_user;
CREATE DATABASE reservation_db OWNER res_user;
CREATE DATABASE order_db       OWNER ord_user;

-- Block the default "everyone can connect" behaviour, then grant back
-- only the owner. Without this, any role could connect to any database.
REVOKE CONNECT ON DATABASE inventory_db   FROM PUBLIC;
REVOKE CONNECT ON DATABASE reservation_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE order_db       FROM PUBLIC;

GRANT CONNECT ON DATABASE inventory_db   TO inv_user;
GRANT CONNECT ON DATABASE reservation_db TO res_user;
GRANT CONNECT ON DATABASE order_db       TO ord_user;
