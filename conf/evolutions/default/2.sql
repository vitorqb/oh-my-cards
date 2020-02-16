-- oneTimePasswords Schema

-- !Ups
CREATE TABLE oneTimePasswords (
    id TEXT PRIMARY KEY UNIQUE NOT NULL,
    email TEXT NOT NULL,
    oneTimePassword TEXT NOT NULL,
    expirationDateTime DATETIME NOT NULL,
    hasBeenUsed BOOLEAN NOT NULL,
    hasBeenInvalidated BOOLEAN NOT NULL
)

-- !Downs
DROP TABLE oneTimePasswords
