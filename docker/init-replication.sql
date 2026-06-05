-- Executado pelo postgres primário no primeiro boot (docker-entrypoint-initdb.d)
-- Cria o usuário de replicação usado pela réplica para conectar via streaming WAL.
-- Sem este usuário, o pg_basebackup na réplica falha com "authentication failed".
CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator';

-- Permite que a réplica se autentique como replicator
-- (equivalente a adicionar linha no pg_hba.conf, mas via SQL é mais portátil em Docker)
SELECT pg_reload_conf();

