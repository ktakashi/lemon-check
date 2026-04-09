-- Initial test data for petstore scenarios
INSERT INTO pets (id, name, status, category, price, created_at, updated_at) VALUES
(1, 'Max', 'AVAILABLE', 'dog', 299.99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Bella', 'AVAILABLE', 'cat', 199.99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Charlie', 'PENDING', 'dog', 349.99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Luna', 'SOLD', 'cat', 249.99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Reset sequence to avoid ID conflicts with manually inserted data
ALTER TABLE pets ALTER COLUMN id RESTART WITH 100;

-- Tags for pets
INSERT INTO pet_tags (pet_id, tag) VALUES
(1, 'friendly'),
(1, 'trained'),
(2, 'playful'),
(3, 'puppy'),
(4, 'senior');
