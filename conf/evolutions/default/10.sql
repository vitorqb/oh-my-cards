-- Add isAdmin to users

-- !Ups
BEGIN TRANSACTION;

ALTER TABLE users RENAME TO _users_old;

CREATE TABLE users (
    id TEXT PRIMARY KEY UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    isAdmin BOOLEAN NOT NULL
);

INSERT INTO users(id, email, isAdmin)
SELECT id, email, FALSE FROM _users_old;

DROP TABLE _users_old;

CREATE INDEX users_id ON users(id);
CREATE INDEX users_email ON users(email);

COMMIT;


-- !Downs
BEGIN TRANSACTION;

DROP INDEX users_id;
DROP INDEX users_email;

ALTER TABLE users RENAME TO _users_old;

CREATE TABLE users (
    id TEXT PRIMARY KEY UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL
);

INSERT INTO users(id, email) SELECT id, email FROM _users_old;

DROP TABLE _users_old;

COMMIT;
