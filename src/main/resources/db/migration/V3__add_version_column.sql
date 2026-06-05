-- Adiciona coluna de versao para controle otimista de concorrencia (Bloco 2)
ALTER TABLE orders ADD COLUMN version BIGINT DEFAULT 0;
