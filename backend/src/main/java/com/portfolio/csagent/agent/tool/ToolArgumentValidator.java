package com.portfolio.csagent.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.llm.ToolSpec;
import org.springframework.stereotype.Component;

@Component
public class ToolArgumentValidator {
    @SuppressWarnings("unchecked")
    public void validate(ToolSpec spec, Map<String, Object> args) {
        Map<String, Object> schema = spec.parameters();
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        for (String key : required) {
            Object value = args.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BizException(400, "工具参数缺失：" + key);
            }
        }
        if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            Set<String> allowed = properties.keySet();
            args.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                    .ifPresent(key -> {
                        throw new BizException(400, "工具参数不允许：" + key);
                    });
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getValue() == null || !properties.containsKey(entry.getKey())) {
                continue;
            }
            Map<String, Object> property = (Map<String, Object>) properties.get(entry.getKey());
            if ("string".equals(property.get("type")) && !(entry.getValue() instanceof String)) {
                throw new BizException(400, "工具参数类型不正确：" + entry.getKey());
            }
            String value = String.valueOf(entry.getValue());
            int maxLength = ((Number) property.getOrDefault("maxLength", 255)).intValue();
            if (value.length() > maxLength) {
                throw new BizException(400, "工具参数过长：" + entry.getKey());
            }
            List<String> values = (List<String>) property.get("enum");
            if (values != null && !values.contains(value)) {
                throw new BizException(400, "工具参数取值不正确：" + entry.getKey());
            }
            String pattern = (String) property.get("pattern");
            if (pattern != null && !Pattern.matches(pattern, value)) {
                throw new BizException(400, "工具参数格式不正确：" + entry.getKey());
            }
        }
    }
}
