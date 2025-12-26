package org.aouessar.renderer.atlas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal JSON reader tailored to our atlas.json output.
 * No external dependencies.
 *
 * Expected shape:
 * {
 *   "tiles": {
 *     "grass": { "u0":..., "v0":..., "u1":..., "v1":... },
 *     ...
 *   }
 * }
 */
public final class AtlasLoader {

    public Atlas loadFromResources(String resourcePath) {
        String json = readAll(resourcePath);
        Map<String, Object> root = MiniJson.parseObject(json);

        Object tilesObj = root.get("tiles");
        if (!(tilesObj instanceof Map<?, ?> tilesMapRaw)) {
            throw new IllegalStateException("atlas.json missing object: tiles");
        }

        Map<String, Atlas.UvRect> tiles = new HashMap<>();

        for (var e : tilesMapRaw.entrySet()) {
            String tileName = (String) e.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> tile = (Map<String, Object>) e.getValue();

            float u0 = ((Number) tile.get("u0")).floatValue();
            float v0 = ((Number) tile.get("v0")).floatValue();
            float u1 = ((Number) tile.get("u1")).floatValue();
            float v1 = ((Number) tile.get("v1")).floatValue();

            tiles.put(tileName, new Atlas.UvRect(u0, v0, u1, v1));
        }

        return new Atlas(tiles);
    }

    private static String readAll(String resourcePath) {
        InputStream in = AtlasLoader.class.getResourceAsStream(resourcePath);
        if (in == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(4096);
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed reading resource: " + resourcePath, e);
        }
    }

    /**
     * Tiny JSON parser (object-only, numbers/strings/bools/null, nested objects).
     * Enough for atlas.json.
     */
    private static final class MiniJson {
        private final String s;
        private int i;

        private MiniJson(String s) {
            this.s = s;
        }

        static Map<String, Object> parseObject(String s) {
            MiniJson p = new MiniJson(s);
            p.skipWs();
            Object v = p.readValue();
            if (!(v instanceof Map<?, ?>)) throw new IllegalArgumentException("Root is not an object");
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) v;
            return out;
        }

        private Object readValue() {
            skipWs();
            if (peek('{')) return readObject();
            if (peek('"')) return readString();
            if (peek('-') || isDigit(peekChar())) return readNumber();
            if (match("true")) return Boolean.TRUE;
            if (match("false")) return Boolean.FALSE;
            if (match("null")) return null;
            throw error("Unexpected value");
        }

        private Map<String, Object> readObject() {
            expect('{');
            skipWs();
            Map<String, Object> m = new HashMap<>();
            if (peek('}')) { i++; return m; }

            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object val = readValue();
                m.put(key, val);
                skipWs();
                if (peek('}')) { i++; break; }
                expect(',');
            }
            return m;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char n = s.charAt(i++);
                    switch (n) {
                        case '"', '\\', '/' -> sb.append(n);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            int code = Integer.parseInt(s.substring(i, i + 4), 16);
                            i += 4;
                            sb.append((char) code);
                        }
                        default -> throw error("Bad escape: \\" + n);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number readNumber() {
            int start = i;
            if (peek('-')) i++;
            while (i < s.length() && isDigit(s.charAt(i))) i++;
            if (peek('.')) {
                i++;
                while (i < s.length() && isDigit(s.charAt(i))) i++;
            }
            if (peek('e') || peek('E')) {
                i++;
                if (peek('+') || peek('-')) i++;
                while (i < s.length() && isDigit(s.charAt(i))) i++;
            }
            String num = s.substring(start, i);
            return Double.parseDouble(num);
        }

        private boolean match(String kw) {
            skipWs();
            if (s.regionMatches(i, kw, 0, kw.length())) {
                i += kw.length();
                return true;
            }
            return false;
        }

        private void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        private void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) throw error("Expected '" + c + "'");
            i++;
        }

        private boolean peek(char c) {
            skipWs();
            return i < s.length() && s.charAt(i) == c;
        }

        private char peekChar() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private RuntimeException error(String msg) {
            return new IllegalArgumentException(msg + " at pos " + i);
        }
    }
}