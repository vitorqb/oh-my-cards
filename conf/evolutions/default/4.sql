-- userTokens Schema

-- !Ups
CREATE TABLE userTokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    userId TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    expirationDateTime DATETIME NOT NULL,
    hasBeenInvalidated BOOLEAN NOT NULL
)

-- !Downs
DROP TABLE userTokens
