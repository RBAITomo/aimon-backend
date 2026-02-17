-- V3__seed_dummy_user.sql
-- Seed dummy parent, user (id=1), and pet profile for development/testing

SET search_path TO aimon, public;

INSERT INTO parents (id, name, email, phone)
VALUES (1, 'Test Parent', 'test@aimon.dev', '0901234567')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, name, age, parent_id, preferences)
VALUES (1, 'Bé Mon', 7, 1, '{"language":"vi","interests":["animals","space","music"]}')
ON CONFLICT (id) DO NOTHING;

INSERT INTO pet_profiles (user_id, name, stage, hunger, energy, happiness, xp, level)
VALUES (1, 'Coneko', 'EGG', 50, 100, 80, 0, 1)
ON CONFLICT (user_id) DO NOTHING;

-- Reset sequences to avoid id conflicts on next insert
SELECT setval('parents_id_seq', GREATEST((SELECT MAX(id) FROM parents), 1));
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), 1));
