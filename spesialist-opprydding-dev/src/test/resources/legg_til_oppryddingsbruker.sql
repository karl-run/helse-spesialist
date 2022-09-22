DO $$
    BEGIN
        CREATE ROLE "spesialist-opprydding-dev" login password 'ryddepassord';
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            GRANT USAGE, SELECT ON SEQUENCES TO "spesialist-opprydding-dev";
    EXCEPTION
        WHEN duplicate_object THEN RAISE NOTICE '%, whatever', SQLERRM USING ERRCODE = SQLSTATE;
    END
$$;
