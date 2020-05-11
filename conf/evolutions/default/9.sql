-- Add createdAt and updatedAt to cards

-- !Ups
BEGIN TRANSACTION;

ALTER TABLE cards RENAME TO _cards_old;

CREATE TABLE cards (
    id TEXT NOT NULL UNIQUE PRIMARY KEY,
    userId TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT,
    createdAt DATETIME,
    updatedAt DATETIME
);

INSERT INTO cards(id, userId, title, body, createdAt, updatedAt)
SELECT id, "defaultUser@ohmycards.com", title, body, NULL, NULL FROM _cards_old;

DROP TABLE _cards_old;

CREATE INDEX cards_id ON cards(id);
CREATE INDEX cards_userId ON cards(userId);

COMMIT;


-- !Downs
BEGIN TRANSACTION;

DROP INDEX cards_id;
DROP INDEX cards_userId;

ALTER TABLE cards RENAME TO _cards_old;

CREATE TABLE cards (
    id TEXT NOT NULL UNIQUE PRIMARY KEY,
    userId TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT
);

INSERT INTO cards(id, userId, title, body) SELECT id, userId, title, body FROM _cards_old;

DROP TABLE _cards_old;

COMMIT;
