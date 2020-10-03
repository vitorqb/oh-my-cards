-- Adds cardHistoricalEvents

-- !Ups
BEGIN TRANSACTION;

CREATE TABLE cardHistoricalEvents (
  id TEXT NOT NULL UNIQUE PRIMARY KEY,
  cardId TEXT NOT NULL,
  userId TEXT NOT NULL,
  datetime DATETIME NOT NULL,
  eventType TEXT NOT NULL
);

CREATE TABLE cardStringFieldUpdates (
  id TEXT NOT NULL UNIQUE PRIMARY KEY,
  coreEventId TEXT NOT NULL,
  fieldName TEXT NOT NULL,
  oldValue TEXT NOT NULL,
  newValue TEXT NOT NULL
);

COMMIT;


-- !Downs
BEGIN TRANSACTION;

DROP TABLE cardHistoricalEvents;
DROP TABLE cardStringFieldUpdates;

COMMIT;
