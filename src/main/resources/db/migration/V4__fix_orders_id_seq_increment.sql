-- Fix: align the database sequence increment with the Hibernate allocationSize.
-- OrderEntity uses @SequenceGenerator(allocationSize = 50), which means Hibernate
-- pre-allocates blocks of 50 IDs in memory and expects the DB sequence to advance
-- by 50 on every nextval() call. With INCREMENT BY 1 (BIGSERIAL default) the
-- application would reuse IDs already handed out by the DB, causing duplicate-key
-- violations under concurrent load.
ALTER SEQUENCE orders_id_seq INCREMENT BY 50;

