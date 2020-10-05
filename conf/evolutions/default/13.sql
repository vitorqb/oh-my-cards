-- Adds cardHistoricalEvents

-- !Ups
BEGIN TRANSACTION;

CREATE TABLE counters (id TEXT NOT NULL UNIQUE PRIMARY KEY, value INTEGER NOT NULL);
INSERT INTO counters(id, value) VALUES ('baseCounter', 1000);

COMMIT;


-- !Downs
BEGIN TRANSACTION;

DROP TABLE counters;

COMMIT;
