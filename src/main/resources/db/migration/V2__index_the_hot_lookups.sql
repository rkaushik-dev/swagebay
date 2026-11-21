-- Index the lookups that had been scanning whole tables: dose by badge and year, the indications
-- on a film, the day log and the unread listing on radiographs, both survey lookups, a source's
-- open trip, and the welds of a job.

CREATE INDEX ix_dose_entry_badge_year ON dose_entry (badge, year);

CREATE INDEX ix_indication_radiograph_id ON indication (radiograph_id);

CREATE INDEX ix_radiograph_exposed_at ON radiograph (exposed_at);
CREATE INDEX ix_radiograph_read_at ON radiograph (read_at);

CREATE INDEX ix_survey_performed_at ON survey (performed_at);
CREATE INDEX ix_survey_radiograph_number ON survey (radiograph_number);

CREATE INDEX ix_trip_source_id_came_back ON trip (source_id, came_back);

CREATE INDEX ix_weld_job_id ON weld (job_id);
