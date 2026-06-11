-- Seed de dados iniciais para demonstração em aula

-- 3 produtos no inventário
INSERT INTO inventory (product_id, name, quantity)
VALUES ('prod-tenis', 'Tênis Nike Air Max 42 azul', 5000),
       ('prod-camisa', 'Camisa Polo M branca', 10),
       ('prod-livro', 'Designing Data-Intensive Applications', 3);

-- 2 pedidos existentes
INSERT INTO orders (customer_id, status, total_amount, created_at)
VALUES ('customer-alice', 'PAID', 350.00, '2026-06-01 10:00:00'),
       ('customer-bob', 'CREATED', 129.90, '2026-06-01 11:00:00');

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price)
VALUES (1, 'prod-tenis', 'Tênis Nike Air Max 42 azul', 1, 350.00),
       (2, 'prod-livro', 'Designing Data-Intensive Applications', 1, 129.90);

-- Avança a sequência para evitar conflito com IDs inseridos explicitamente
SELECT setval('orders_id_seq', 100);
SELECT setval('order_items_id_seq', 100);

