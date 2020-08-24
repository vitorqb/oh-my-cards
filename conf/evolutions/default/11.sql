-- Add ref to cards

-- !Ups
BEGIN TRANSACTION;

ALTER TABLE cards RENAME TO _cards_old;
DROP INDEX cards_id;
DROP INDEX cards_userId;

CREATE TABLE cards (
  id TEXT NOT NULL UNIQUE PRIMARY KEY,
  userId TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT,
  createdAt DATETIME,
  updatedAt DATETIME,
  ref INTEGER UNIQUE 
);
CREATE INDEX cards_id ON cards(id);
CREATE INDEX cards_userId ON cards(userId);
CREATE INDEX cards_ref ON cards(ref);

INSERT INTO cards(id, userId, title, body, createdAt, updatedAt)
SELECT id, userId, title, body, createdAt, updatedAt FROM _cards_old;

-- Trick to add 1 2 3... to ref
UPDATE cards SET ref = (SELECT COUNT(*) FROM cards c1 WHERE c1.id >= cards.id);

DROP TABLE _cards_old;

COMMIT;


-- !Downs
BEGIN TRANSACTION;

DROP INDEX cards_id;
DROP INDEX cards_userId;
DROP INDEX cards_ref;

ALTER TABLE cards RENAME TO _cards_old;

CREATE TABLE cards (
  id TEXT NOT NULL UNIQUE PRIMARY KEY,
  userId TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT,
  createdAt DATETIME,
  updatedAt DATETIME
);

CREATE INDEX cards_id ON cards(id);
CREATE INDEX cards_userId ON cards(userId);

INSERT INTO cards(id, userId, title, body, createdAt, updatedAt)
SELECT id, userId, title, body, createdAt, updatedAt FROM _cards_old;

DROP TABLE _cards_old;

COMMIT;
