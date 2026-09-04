ALTER TABLE public.sports_roster_member
    ADD COLUMN affiliation_start_date date NULL,
    ADD COLUMN affiliation_end_date date NULL,
    ADD CONSTRAINT ck_sports_roster_member_affiliation_period
        CHECK (
            affiliation_end_date IS NULL
            OR affiliation_start_date IS NULL
            OR affiliation_end_date >= affiliation_start_date
        );

ALTER TABLE public.sports_roster_publication_member
    ADD COLUMN affiliation_start_date date NULL,
    ADD COLUMN affiliation_end_date date NULL,
    ADD CONSTRAINT ck_sports_roster_pub_member_affiliation_period
        CHECK (
            affiliation_end_date IS NULL
            OR affiliation_start_date IS NULL
            OR affiliation_end_date >= affiliation_start_date
        );
