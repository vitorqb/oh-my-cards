-- cardGridProfile

-- !Ups
BEGIN TRANSACTION;

CREATE TABLE cardGridConfigs (
    id TEXT PRIMARY KEY NOT NULL UNIQUE,
    profileId TEXT NOT NULL UNIQUE,
    page INTEGER,
    pageSize INTEGER
);
CREATE INDEX cardGridConfigs__profileId ON cardGridConfigs(profileId);

CREATE TABLE cardGridConfigIncludeTags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    configId TEXT NOT NULL,
    tag TEXT NOT NULL
);
CREATE INDEX cardGridConfigIncludeTags__configId ON cardGridConfigIncludeTags(configId);

CREATE TABLE cardGridConfigExcludeTags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    configId TEXT NOT NULL,
    tag TEXT NOT NULL
);
CREATE INDEX cardGridConfigExcludeTags__configId ON cardGridConfigExcludeTags(configId);

CREATE TABLE cardGridProfiles (
    id TEXT PRIMARY KEY NOT NULL UNIQUE,
    userId TEXT NOT NULL,
    name TEXT NOT NULL,
    UNIQUE(userId, name)
);

COMMIT;

-- !Downs
BEGIN TRANSACTION;

DROP INDEX cardGridConfigs__profileId;
DROP INDEX cardGridConfigExcludeTags__configId;
DROP INDEX cardGridConfigIncludeTags__configId;
DROP TABLE cardGridConfigs;
DROP TABLE cardGridConfigIncludeTags;
DROP TABLE cardGridConfigExcludeTags;
DROP TABLE cardGridProfiles;

COMMIT;
