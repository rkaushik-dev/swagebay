-- A reread is a second reading of the same film by a second person, kept beside the first rather
-- than over it. A reread cannot exist without a first reading, and the check says so.

ALTER TABLE radiograph ADD COLUMN reread_by CHARACTER VARYING(40);
ALTER TABLE radiograph ADD COLUMN reread_at TIMESTAMP(6);
ALTER TABLE radiograph ADD COLUMN reread_reason CHARACTER VARYING(200);
ALTER TABLE radiograph ADD CONSTRAINT ck_radiograph_reread_follows_a_reading
    CHECK (reread_by IS NULL OR read_by IS NOT NULL);
