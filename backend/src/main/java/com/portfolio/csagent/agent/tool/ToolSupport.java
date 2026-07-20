package com.portfolio.csagent.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工具通用小工具：参数取值 + JSON Schema 构造。 */
public final class ToolSupport {

    private ToolSupport() {
    }

    public static String str(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    public static Map<String, Object> prop(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("description", description);
        m.put("maxLength", 255);
        return m;
    }

    public static Map<String, Object> enumProp(String description, List<String> values) {
        Map<String, Object> property = prop("string", description);
        property.put("enum", values);
        return property;
    }

    public static Map<String, Object> dateProp(String description) {
        Map<String, Object> property = prop("string", description);
        property.put("pattern", "\\d{4}-\\d{2}-\\d{2}");
        return property;
    }

    public static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", required);
        m.put("additionalProperties", false);
        return m;
    }
}
