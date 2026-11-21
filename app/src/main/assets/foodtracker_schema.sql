PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  username TEXT NOT NULL,
  name TEXT NOT NULL,
  quantity INTEGER NOT NULL,
  price REAL NOT NULL,
  createdDateEpochDay INTEGER NOT NULL,
  productionDateEpochDay INTEGER,
  expireDateEpochDay INTEGER NOT NULL,
  imageUri TEXT,
  tagsJson TEXT,
  isArchived INTEGER NOT NULL,
  storageState TEXT,
  isOpened INTEGER
);

CREATE INDEX IF NOT EXISTS index_products_username ON products(username);

CREATE TABLE IF NOT EXISTS users (
  username TEXT PRIMARY KEY NOT NULL,
  passwordHash TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_profiles (
  username TEXT PRIMARY KEY NOT NULL,
  displayUsername TEXT NOT NULL,
  nickname TEXT NOT NULL,
  avatarUri TEXT
);

CREATE TABLE IF NOT EXISTS auth_state (
  id INTEGER PRIMARY KEY NOT NULL,
  currentUser TEXT
);
