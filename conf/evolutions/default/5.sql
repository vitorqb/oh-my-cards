-- Adds user to cards

-- !Ups
BEGIN TRANSACTION;

ALTER TABLE cards RENAME TO _cards_old;

CREATE TABLE cards (
    id TEXT NOT NULL UNIQUE PRIMARY KEY,
    userId TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT
);

INSERT INTO cards(id, userId, title, body)
SELECT id, "defaultUser@ohmycards.com", title, body FROM _cards_old;

DROP TABLE _cards_old;

COMMIT;


-- !Downs
BEGIN TRANSACTION;

ALTER TABLE cards RENAME TO _cards_old;

CREATE TABLE cards (
    id TEXT NOT NULL UNIQUE PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT
);

INSERT INTO cards(id, title, body) SELECT id, title, body FROM _cards_old;

DROP TABLE _cards_old;

COMMIT;
