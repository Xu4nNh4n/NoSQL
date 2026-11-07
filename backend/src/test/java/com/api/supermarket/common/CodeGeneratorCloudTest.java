package com.api.supermarket.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.api.supermarket.support.CloudTest;

@CloudTest
class CodeGeneratorCloudTest {
    @Autowired
    CodeGenerator codes;

    @Test
    void concurrentCallsGiveDistinctConsecutiveCodes() throws Exception {
        List<Callable<String>> calls = IntStream.range(0, 50).<Callable<String>>mapToObj(i -> () -> codes.next(DocumentType.ORDER)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        Set<Long> seqs = new TreeSet<>();
        try {
            for (Future<String> f : pool.invokeAll(calls)) {
                String code = f.get();
                seqs.add(Long.parseLong(code.substring(code.indexOf('-') + 1)));
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(50, seqs.size());
        long first = seqs.iterator().next();
        assertEquals(first + 49, ((TreeSet<Long>) seqs).last());
    }
}
