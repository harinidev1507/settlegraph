ALTER TABLE app_user ADD COLUMN name VARCHAR(200);

UPDATE app_user SET name = trim(both ' ' from (first_name || ' ' || last_name));

ALTER TABLE app_user ALTER COLUMN name SET NOT NULL;

ALTER TABLE app_user DROP COLUMN first_name;
ALTER TABLE app_user DROP COLUMN last_name;
