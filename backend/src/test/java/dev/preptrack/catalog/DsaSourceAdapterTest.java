package dev.preptrack.catalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DsaSourceAdapterTest {
    @Test
    void parsesQuotedTopicListsAndKeepsSourceFields() {
        var items=new ArrayList<DsaSourceAdapter.Item>();
        DsaSourceAdapter.parseCompanyCsv("Amazon", "Difficulty,Title,Frequency,Acceptance Rate,Link,Topics\r\n"+
            "EASY,Two Sum,100.0,0.0058,https://leetcode.com/problems/two-sum,\"Array, Hash Table\"\r\n",items);
        assertEquals(1,items.size());
        assertEquals("Two Sum",items.getFirst().title());
        assertEquals("Amazon/5. All.csv/https://leetcode.com/problems/two-sum",items.getFirst().externalKey());
        assertEquals(java.util.List.of("Array","Hash Table"),items.getFirst().topics());
        assertEquals("EASY",items.getFirst().metadata().get("difficulty"));
    }

    @Test
    void ignoresRowsWithoutAnAllowedCanonicalLeetCodeLink() {
        var items=new ArrayList<DsaSourceAdapter.Item>();
        DsaSourceAdapter.parseCompanyCsv("Example", "Difficulty,Title,Frequency,Acceptance Rate,Link,Topics\n"+
            "EASY,Untrusted Row,10,0.2,https://example.com/problem,array\n",items);
        assertTrue(items.isEmpty());
    }

    @Test
    void importsRowsWhenUpstreamOmitsOptionalTopicAndDifficultyColumns() {
        var items=new ArrayList<DsaSourceAdapter.Item>();
        DsaSourceAdapter.parseCompanyCsv("BILL Holdings", "Title,Link\n"+
            "Example Problem,https://leetcode.com/problems/example-problem\n",items);
        assertEquals(1,items.size());
        assertTrue(items.getFirst().topics().isEmpty());
        assertTrue(items.getFirst().metadata().isEmpty());
    }
}
