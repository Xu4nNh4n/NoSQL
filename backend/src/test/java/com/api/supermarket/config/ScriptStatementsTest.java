package com.api.supermarket.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScriptStatementsTest {

    @Test
    void splitsAndDropsComments() {
        List<String> parts = ScriptStatements.split("-- note\nCREATE A;\n\n// x\nCREATE B (\n a int\n);\n");
        assertEquals(List.of("CREATE A", "CREATE B (\n a int\n)"), parts);
    }

    @Test
    void bundledScriptsParse() {
        List<String> cql = ScriptStatements.load("cassandra/schema.cql");
        assertEquals(4, cql.size());
        assertTrue(cql.stream().allMatch(s -> s.startsWith("CREATE TABLE IF NOT EXISTS")));
        assertEquals(5, ScriptStatements.load("neo4j/constraints.cypher").size());
    }
}
