DO $$
DECLARE
    database_name text := current_database();
    runtime_role text := current_user;
BEGIN
    EXECUTE format(
        'ALTER DATABASE %I SET betufu.runtime_role TO %L',
        database_name,
        runtime_role
    );
END;
$$;
