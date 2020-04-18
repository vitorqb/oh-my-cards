-- Add the column query to card grid profile

-- !Ups
ALTER TABLE cardGridConfigs ADD query TEXT;

-- !Downs
BEGIN TRANSACTION;

DROP INDEX cardGridConfigs__profileId;
ALTER TABLE cardGridConfigs RENAME TO _cardGridConfigs_old;

CREATE TABLE cardGridConfigs (
    id TEXT PRIMARY KEY NOT NULL UNIQUE,
    profileId TEXT NOT NULL UNIQUE,
    page INTEGER,
    pageSize INTEGER
);
CREATE INDEX cardGridConfigs__profileId ON cardGridConfigs(profileId);

INSERT INTO cardGridConfigs(id, profileId, page, pageSize)
SELECT id, profileId, page, pageSize FROM _cardGridConfigs_old;

DROP TABLE _cardGridConfigs_old;

COMMIT;
