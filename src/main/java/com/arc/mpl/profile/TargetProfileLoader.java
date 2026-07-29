package com.arc.mpl.profile;

import com.arc.mpl.hir.ValueType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads the compiler-facing subset of a versioned target profile JSON document. */
final class TargetProfileLoader {
    static final int SCHEMA_VERSION = 1;

    private TargetProfileLoader() {
    }

    static TargetProfile load(InputStream source) {
        if (source == null) throw new IllegalArgumentException("找不到目标配置资源");
        try (source) {
            Map<String, Object> root = object(JsonDocumentParser.parse(
                new String(source.readAllBytes(), StandardCharsets.UTF_8)), "根节点");
            if (requiredInt(root, "schemaVersion") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("不支持的目标配置 schemaVersion：" + requiredInt(root, "schemaVersion"));
            }

            Map<TargetProfile.ProcessorKind, Integer> processors = new EnumMap<>(TargetProfile.ProcessorKind.class);
            Map<String, Object> processorNodes = requiredObject(root, "processors");
            processors.put(TargetProfile.ProcessorKind.MICRO, requiredInt(requiredObject(processorNodes, "micro"), "ipt"));
            processors.put(TargetProfile.ProcessorKind.LOGIC, requiredInt(requiredObject(processorNodes, "logic"), "ipt"));
            processors.put(TargetProfile.ProcessorKind.HYPER, requiredInt(requiredObject(processorNodes, "hyper"), "ipt"));

            Map<String, Object> limits = requiredObject(root, "limits");
            Map<String, Object> hardware = requiredObject(root, "hardware");
            Map<String, Object> contents = requiredObject(root, "contents");
            UnitData units = unitData(contents);
            return new JsonTargetProfile(
                requiredText(root, "id"), stringSet(root, "capabilities"),
                requiredInt(requiredObject(hardware, "memoryCell"), "capacity"),
                requiredInt(requiredObject(hardware, "memoryBank"), "capacity"),
                processors,
                requiredInt(limits, "maxInstructions"),
                requiredInt(limits, "maxJumpLabels"),
                requiredInt(limits, "maxTokensPerStatement"),
                requiredInt(limits, "maxGraphicsBufferCommands"),
                requiredInt(limits, "displayFlushCommandLimit"),
                requiredInt(limits, "maxMessageUtf16CodeUnits"),
                requiredInt(limits, "maxDrawCoordinateMagnitude"),
                units.types(), units.properties(), units.actions(), buildingData(contents),
                instructionData(root), macroData(root));
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取目标配置 JSON", exception);
        }
    }

    private static Map<String, Object> requiredObject(Map<String, Object> node, String field) {
        return object(node.get(field), field);
    }

    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("目标配置缺少对象字段：" + field);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("目标配置对象键必须是字符串：" + field);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static int requiredInt(Map<String, Object> node, String field) {
        Object value = node.get(field);
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
            || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("目标配置缺少整数字段：" + field);
        }
        return number.intValue();
    }

    private static String requiredText(Map<String, Object> node, String field) {
        Object value = node.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("目标配置缺少字符串字段：" + field);
        }
        return text;
    }

    private static Set<String> stringSet(Map<String, Object> node, String field) {
        Object value = node.get(field);
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("目标配置缺少数组字段：" + field);
        Set<String> result = new LinkedHashSet<>();
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("目标配置字段 " + field + " 只能包含非空字符串");
            }
            result.add(text);
        }
        return Set.copyOf(result);
    }

    private static UnitData unitData(Map<String, Object> contents) {
        Map<String, TargetProfile.UnitType> types = new LinkedHashMap<>();
        Map<String, ValueType> properties = new LinkedHashMap<>();
        Map<String, TargetProfile.UnitAction> actions = new LinkedHashMap<>();
        for (Object entry : requiredList(contents, "units")) {
            Map<String, Object> unit = object(entry, "contents.units[]");
            String mplType = requiredText(unit, "mplType");
            TargetProfile.UnitType type = new TargetProfile.UnitType(
                requiredText(unit, "mlogName"), requiredBoolean(unit, "logicControllable"));
            if (types.put(mplType, type) != null) {
                throw new IllegalArgumentException("目标配置重复的 Unit 类型：" + mplType);
            }
            for (Object field : requiredList(unit, "fields")) {
                Map<String, Object> member = object(field, "contents.units[].fields[]");
                String name = requiredText(member, "name");
                ValueType valueType = valueType(requiredText(member, "type"), "Unit 字段 " + name);
                ValueType previous = properties.putIfAbsent(name, valueType);
                if (previous != null && previous != valueType) {
                    throw new IllegalArgumentException("目标配置的 Unit 字段类型不一致：" + name);
                }
            }
            for (Object action : requiredList(unit, "actions")) {
                Map<String, Object> member = object(action, "contents.units[].actions[]");
                String name = requiredText(member, "name");
                List<ValueType> parameterTypes = new ArrayList<>();
                for (Object parameter : requiredList(member, "parameters")) {
                    if (!(parameter instanceof String text)) {
                        throw new IllegalArgumentException("Unit 动作参数类型必须是字符串：" + name);
                    }
                    parameterTypes.add(valueType(text, "Unit 动作 " + name));
                }
                TargetProfile.UnitAction definition = new TargetProfile.UnitAction(name, parameterTypes);
                TargetProfile.UnitAction previous = actions.putIfAbsent(name, definition);
                if (previous != null && !previous.parameterTypes().equals(definition.parameterTypes())) {
                    throw new IllegalArgumentException("目标配置的 Unit 动作参数不一致：" + name);
                }
            }
        }
        return new UnitData(Map.copyOf(types), Map.copyOf(properties), Map.copyOf(actions));
    }

    private static Map<String, TargetProfile.BuildingType> buildingData(Map<String, Object> contents) {
        Map<String, TargetProfile.BuildingType> result = new LinkedHashMap<>();
        for (Object entry : requiredList(contents, "buildings")) {
            Map<String, Object> building = object(entry, "contents.buildings[]");
            String mplType = requiredText(building, "mplType");
            Map<String, ValueType> properties = new LinkedHashMap<>();
            for (Object field : requiredList(building, "fields")) {
                Map<String, Object> member = object(field, "contents.buildings[].fields[]");
                String name = requiredText(member, "name");
                if (properties.put(name, valueType(requiredText(member, "type"), "Building 字段 " + name)) != null) {
                    throw new IllegalArgumentException("目标配置重复的 Building 字段：" + mplType + "." + name);
                }
            }
            Map<String, TargetProfile.BuildingAction> actions = new LinkedHashMap<>();
            for (Object action : requiredList(building, "actions")) {
                Map<String, Object> member = object(action, "contents.buildings[].actions[]");
                String name = requiredText(member, "name");
                List<ValueType> parameters = parameterTypes(member, "Building 动作 " + name);
                String target = requiredText(member, "target");
                if (actions.put(name, new TargetProfile.BuildingAction(name, parameters, target)) != null) {
                    throw new IllegalArgumentException("目标配置重复的 Building 动作：" + mplType + "." + name);
                }
            }
            TargetProfile.BuildingType definition = new TargetProfile.BuildingType(
                requiredText(building, "mlogName"), properties, actions);
            if (result.put(mplType, definition) != null) {
                throw new IllegalArgumentException("目标配置重复的 Building 类型：" + mplType);
            }
        }
        return Map.copyOf(result);
    }

    private static List<TargetProfile.Instruction> instructionData(Map<String, Object> root) {
        List<TargetProfile.Instruction> result = new ArrayList<>();
        for (Object entry : requiredList(root, "instructions")) {
            Map<String, Object> instruction = object(entry, "instructions[]");
            List<String> operands = stringList(instruction, "operands");
            result.add(new TargetProfile.Instruction(requiredText(instruction, "opcode"), operands,
                stringSet(instruction, "permissions")));
        }
        return List.copyOf(result);
    }

    private static List<TargetProfile.Macro> macroData(Map<String, Object> root) {
        List<TargetProfile.Macro> result = new ArrayList<>();
        for (Object entry : requiredList(root, "macros")) {
            Map<String, Object> macro = object(entry, "macros[]");
            List<TargetProfile.MacroParameter> parameters = new ArrayList<>();
            for (Object parameter : requiredList(macro, "parameters")) {
                Map<String, Object> member = object(parameter, "macros[].parameters[]");
                parameters.add(new TargetProfile.MacroParameter(requiredText(member, "name"), requiredText(member, "type")));
            }
            Map<String, Object> cost = requiredObject(macro, "maxCost");
            result.add(new TargetProfile.Macro(requiredText(macro, "name"), parameters,
                stringSet(macro, "effects"), new TargetProfile.MacroCost(
                    requiredInt(cost, "instructions"), requiredInt(cost, "virtualSlots"), requiredInt(cost, "physicalSlots")),
                stringList(macro, "lowering")));
        }
        return List.copyOf(result);
    }

    private static List<ValueType> parameterTypes(Map<String, Object> node, String context) {
        List<ValueType> result = new ArrayList<>();
        for (Object parameter : requiredList(node, "parameters")) {
            if (!(parameter instanceof String text)) {
                throw new IllegalArgumentException(context + " 参数类型必须是字符串");
            }
            result.add(valueType(text, context));
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(Map<String, Object> node, String field) {
        Object value = node.get(field);
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("目标配置缺少数组字段：" + field);
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("目标配置字段 " + field + " 只能包含非空字符串");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static List<?> requiredList(Map<String, Object> node, String field) {
        Object value = node.get(field);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("目标配置缺少数组字段：" + field);
        return list;
    }

    private static boolean requiredBoolean(Map<String, Object> node, String field) {
        Object value = node.get(field);
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException("目标配置缺少布尔字段：" + field);
        return bool;
    }

    private static ValueType valueType(String source, String context) {
        try {
            return switch (source) {
                case "Int" -> ValueType.INT;
                case "Float" -> ValueType.FLOAT;
                case "Bool" -> ValueType.BOOL;
                default -> throw new IllegalArgumentException("目标配置不支持的 " + context + " 类型：" + source);
            };
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    private record UnitData(
        Map<String, TargetProfile.UnitType> types,
        Map<String, ValueType> properties,
        Map<String, TargetProfile.UnitAction> actions
    ) {
    }

    /** Minimal standards-compliant JSON reader, kept local so profile lookup stays offline. */
    private static final class JsonDocumentParser {
        private final String text;
        private int index;

        private JsonDocumentParser(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            JsonDocumentParser parser = new JsonDocumentParser(text);
            Object result = parser.value();
            parser.whitespace();
            if (parser.index != text.length()) parser.error("JSON 末尾存在额外内容");
            return result;
        }

        private Object value() {
            whitespace();
            if (index >= text.length()) error("JSON 意外结束");
            return switch (text.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                if (index >= text.length() || text.charAt(index) != '"') error("对象键必须是字符串");
                String key = string();
                whitespace();
                expect(':');
                if (result.containsKey(key)) error("对象键重复：" + key);
                result.put(key, value());
                whitespace();
                if (take('}')) return result;
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') return result.toString();
                if (current < 0x20) error("字符串含有未转义控制字符");
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                if (index >= text.length()) error("字符串转义不完整");
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicode());
                    default -> error("不支持的字符串转义");
                }
            }
            error("字符串未结束");
            return "";
        }

        private char unicode() {
            if (index + 4 > text.length()) error("Unicode 转义不完整");
            String hex = text.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                error("非法 Unicode 转义");
                return 0;
            }
        }

        private Object literal(String literal, Object value) {
            if (!text.startsWith(literal, index)) error("非法 JSON 字面量");
            index += literal.length();
            return value;
        }

        private Number number() {
            int start = index;
            if (take('-')) { }
            digits();
            boolean decimal = false;
            if (take('.')) {
                decimal = true;
                digits();
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) index++;
                digits();
            }
            String value = text.substring(start, index);
            try {
                return decimal ? Double.parseDouble(value) : Long.parseLong(value);
            } catch (NumberFormatException exception) {
                error("非法 JSON 数字：" + value);
                return 0;
            }
        }

        private void digits() {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            if (start == index) error("数字缺少数字部分");
        }

        private void expect(char expected) {
            whitespace();
            if (!take(expected)) error("期望字符：" + expected);
        }

        private boolean take(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void whitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private <T> T error(String message) {
            throw new IllegalArgumentException(message + "（位置 " + index + "）");
        }
    }
}
