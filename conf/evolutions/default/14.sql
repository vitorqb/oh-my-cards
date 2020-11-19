-- Adds resourceUserPermissions

-- !Ups
BEGIN TRANSACTION;

CREATE TABLE resourceUserPermissions (
  resourceKey TEXT NOT NULL UNIQUE PRIMARY KEY,
  userId TEXT NOT NULL
);

COMMIT;


-- !Downs
BEGIN TRANSACTION;

DROP TABLE resourceUserPermissions;

COMMIT;
