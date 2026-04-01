package com.slavacom.vkmidsprint.service.tarantool;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteValueConverterTest {

    @Test
    void toByteStringReturnsSameBytes() {
        byte[] payload = "hello".getBytes();

        ByteString result = ByteValueConverter.toByteString(payload);

        assertArrayEquals(payload, result.toByteArray());
    }

    @Test
    void toByteStringBuildsBytesFromMapWithStringIndexes() {
        Map<String, Integer> map = Map.of("0", 104, "1", 105);

        ByteString result = ByteValueConverter.toByteString(map);

        assertArrayEquals(new byte[]{104, 105}, result.toByteArray());
    }

    @Test
    void toUtf8StringDecodesMapEncodedBytes() {
        Map<String, Integer> map = Map.of("0", 49, "1", 50, "2", 51);

        String result = ByteValueConverter.toUtf8String(map);

        assertEquals("123", result);
    }
}

