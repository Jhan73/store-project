-- The fixed list customers see before adding to the cart; the application enum mirrors it (AllergenListIT).
CREATE TABLE catalog.allergen (
    code varchar(30) PRIMARY KEY
);

INSERT INTO catalog.allergen (code) VALUES
    ('CELERY'),
    ('CRUSTACEANS'),
    ('EGGS'),
    ('FISH'),
    ('GLUTEN'),
    ('LUPIN'),
    ('MILK'),
    ('MOLLUSCS'),
    ('MUSTARD'),
    ('PEANUTS'),
    ('SESAME'),
    ('SOY'),
    ('SULPHITES'),
    ('TREE_NUTS');

-- Default privileges give app DML on every new table; the list is fixed, so only migrations may change it.
REVOKE INSERT, UPDATE, DELETE ON catalog.allergen FROM app;
