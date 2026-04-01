package com.slavacom.vkmidsprint.integration;

import com.slavacom.vkmidsprint.service.KeyValueService;
import com.slavacom.vkmidsprint.service.tarantool.ByteValueConverter;
import com.slavacom.vkmidsprint.service.tarantool.TarantoolResponseParser;
import io.tarantool.client.TarantoolClient;
import io.tarantool.client.factory.TarantoolFactory;
import io.tarantool.mapping.TarantoolResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
class KeyValueServiceIntegrationTest {

    @Container
    static final GenericContainer<?> tarantool = new GenericContainer<>("tarantool/tarantool:2.11.2")
            .withExposedPorts(3301)
            .withCopyFileToContainer(MountableFile.forClasspathResource("tarantool/init-test.lua"), "/opt/tarantool/init.lua")
            .withCommand("tarantool", "/opt/tarantool/init.lua");

    private static TarantoolClient boxClient;
    private static KeyValueService keyValueService;

    @BeforeAll
    static void setUpClient() throws Exception {
        boxClient = TarantoolFactory.box()
                .withHost(tarantool.getHost())
                .withPort(tarantool.getMappedPort(3301))
                .withUser("appuser")
                .withPassword("apppass")
                .build();
        keyValueService = new KeyValueService(boxClient);
    }

    @AfterAll
    static void tearDownClient() throws Exception {
        if (boxClient != null) {
            boxClient.close();
        }
    }

    @BeforeEach
    void clearSpace() throws Exception {
        boxClient.eval("return box.space.kv:truncate()").get();
    }

    @Test
    void putGetAndDeleteRoundTrip() throws Exception {
        keyValueService.put("user:1", "John".getBytes());

        TarantoolResponse<List<?>> getResponse = keyValueService.get("user:1");
        List<?> tuples = TarantoolResponseParser.extractTuples(getResponse);
        List<?> tuple = TarantoolResponseParser.getFirstTuple(tuples);

        assertEquals("user:1", tuple.get(0));
        assertEquals("John", ByteValueConverter.toUtf8String(tuple.get(1)));

        keyValueService.delete("user:1");
        TarantoolResponse<List<?>> afterDelete = keyValueService.get("user:1");
        List<?> afterDeleteTuples = TarantoolResponseParser.extractTuples(afterDelete);
        List<?> afterDeleteTuple = TarantoolResponseParser.getFirstTuple(afterDeleteTuples);
        assertTrue(afterDeleteTuple.isEmpty());
    }

    @Test
    void rangeReturnsOnlyBoundedKeys() throws Exception {
        keyValueService.put("a", "1".getBytes());
        keyValueService.put("b", "2".getBytes());
        keyValueService.put("c", "3".getBytes());
        keyValueService.put("d", "4".getBytes());

        TarantoolResponse<List<?>> rangeResponse = keyValueService.range("b", "c");
        List<?> normalized = TarantoolResponseParser.normalizeTuples(
                TarantoolResponseParser.extractTuples(rangeResponse)
        );

        assertEquals(2, normalized.size());

        List<?> firstTuple = TarantoolResponseParser.asTuple(normalized.get(0));
        List<?> secondTuple = TarantoolResponseParser.asTuple(normalized.get(1));

        assertEquals("b", firstTuple.getFirst());
        assertEquals("c", secondTuple.getFirst());
    }

    @Test
    void countReflectsInsertedRecords() throws Exception {
        keyValueService.put("k1", "v1".getBytes());
        keyValueService.put("k2", "v2".getBytes());

        int count = TarantoolResponseParser.extractCount(keyValueService.count());

        assertEquals(2, count);
    }
}

