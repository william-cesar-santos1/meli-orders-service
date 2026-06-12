-- Aumenta o estoque dos produtos usados no load test (k6/blackfriday.js).
-- Estoque baixo (5-10 unidades) esgotava em segundos com qualquer carga
-- e causava falhas massivas de OutOfStock nos testes de performance.

-- Produtos já existentes no seed — aumentar para suportar carga contínua
UPDATE inventory SET quantity = 99999 WHERE product_id = 'prod-tenis';
UPDATE inventory SET quantity = 99999 WHERE product_id = 'prod-camisa';

-- prod-bone é referenciado pelo k6 mas estava ausente do inventário,
-- causando falha em ~33% das criações de pedido (1 de 3 produtos sorteados).
INSERT INTO inventory (product_id, name, quantity)
VALUES ('prod-bone', 'Boné New Era', 99999)
ON CONFLICT (product_id) DO UPDATE SET quantity = 99999;

