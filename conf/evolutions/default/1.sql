-- Card Schema

-- !Ups
CREATE TABLE cards (
    id TEXT NOT NULL UNIQUE PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT
)

-- !Downs
DROP TABLE cards
