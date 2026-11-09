package br.com.lib.structured.log.core;

import java.util.HashMap;
import java.util.Map;

public class LogRequestAttributes {

    private final Map<String, String> values;

    public LogRequestAttributes(Map<String, String> values) {
        this.values = new HashMap<>(values);
    }

    public String getAttribute(final String key) {
        return values.get(key);
    }

    public void put(final String key, final String value) {
        values.put(key, value);
    }
}
