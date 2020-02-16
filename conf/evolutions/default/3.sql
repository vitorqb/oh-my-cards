-- Users Schema

-- !Ups
CREATE TABLE users (
    id TEXT PRIMARY KEY UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL
)

-- !Downs
DROP TABLE users
