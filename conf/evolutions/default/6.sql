-- cardsTags

-- !Ups
BEGIN TRANSACTION;

CREATE TABLE cardsTags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cardId TEXT NOT NULL,
    tag TEXT NOT NULL,
    UNIQUE(cardId, tag)
);
CREATE INDEX cardIdIndex ON cardsTags(cardId);
CREATE INDEX tagIndex ON cardsTags(tag);

COMMIT;

-- !Downs
BEGIN TRANSACTION;

DROP INDEX cardIdIndex;
DROP INDEX tagIndex;
DROP TABLE cardsTags;

COMMIT;
