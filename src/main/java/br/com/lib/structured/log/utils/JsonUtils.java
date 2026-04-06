package br.com.lib.structured.log.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

    public static Object normalizeOutput(Object output) {
        if (output == null) return null;
        if (!(output instanceof String)) {
            return output;
        }
        String str = (String) output;
        try {
            return new ObjectMapper().readTree(str);
        } catch (Exception e) {
            return str;
        }
    }
}
