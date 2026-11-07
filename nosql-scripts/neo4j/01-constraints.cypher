// 01-constraints.cypher – định danh duy nhất cho mọi node (SAO Y backend/src/main/resources/neo4j/constraints.cypher).
// Chạy trong Neo4j Browser / Workspace của Aura, hoặc: cypher-shell -a $NEO4J_URI -u neo4j -p ... -f 01-constraints.cypher
// Định danh duy nhất cho mọi node, để MERGE theo id luôn an toàn khi ghi lặp.
CREATE CONSTRAINT order_id IF NOT EXISTS FOR (n:Order) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT product_id IF NOT EXISTS FOR (n:Product) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT batch_id IF NOT EXISTS FOR (n:Batch) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT batch_code IF NOT EXISTS FOR (n:Batch) REQUIRE n.code IS UNIQUE;
CREATE CONSTRAINT supplier_id IF NOT EXISTS FOR (n:Supplier) REQUIRE n.id IS UNIQUE;
CREATE INDEX order_at IF NOT EXISTS FOR (o:Order) ON (o.at);
