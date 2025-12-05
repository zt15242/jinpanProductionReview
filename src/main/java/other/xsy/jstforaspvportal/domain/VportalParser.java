package other.xsy.jstforaspvportal.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析String到内部类
 * 
 * @author System
 * @version 1.0
 */
public class VportalParser {
    
    /**
     * JSON转换为Vportal对象
     */
    public static VportalInterface jsonToVportal(String jsonContent) throws Exception {
        VportalInterface vr = new VportalInterface() {
            @Override
            public String getType() {
                return null;
            }
        };
        
        // 使用简单的字符串解析替代Jackson
        Map<String, Object> objMap = parseSimpleJson(jsonContent);
        
        if ((objMap.containsKey("request") && objMap.get("request") != null) || 
            (objMap.containsKey("response") && objMap.get("response") != null)) {
            
            Map<String, Object> requestMap = new HashMap<>();
            
            if (objMap.containsKey("request") && objMap.get("request") != null) {
                vr = new VportalRequest();
                requestMap = (Map<String, Object>) objMap.get("request");
            } else if (objMap.containsKey("response") && objMap.get("response") != null) {
                vr = new VportalRespone();
                requestMap = (Map<String, Object>) objMap.get("response");
            }
            
            if (requestMap.containsKey("head") && requestMap.get("head") != null) {
                Map<String, Object> headsMap = (Map<String, Object>) requestMap.get("head");
                vr.heads = headsMap;
            }
            
            if (requestMap.containsKey("fields") && requestMap.get("fields") != null) {
                Map<String, Object> fieldsMap = (Map<String, Object>) requestMap.get("fields");
                vr.fields = fieldsMap;
            }
            
            if (requestMap.containsKey("tables") && requestMap.get("tables") != null) {
                for (Object tableObject : (List<Object>) requestMap.get("tables")) {
                    Map<String, Object> tableMap = (Map<String, Object>) tableObject;
                    VportalTable vTable;
                    
                    if (tableMap.containsKey("tablename") && tableMap.get("tablename") != null) {
                        vTable = new VportalTable(String.valueOf(tableMap.get("tablename")));
                    } else {
                        vTable = new VportalTable("tablename");
                    }
                    
                    if (tableMap.containsKey("type") && tableMap.get("type") != null) {
                        vTable.setType(String.valueOf(tableMap.get("type")));
                    }
                    
                    if (tableMap.containsKey("thead") && tableMap.get("thead") != null) {
                        List<Object> theadList = (List<Object>) tableMap.get("thead");
                        for (Object theadStr : theadList) {
                            vTable.getThead().add(String.valueOf(theadStr));
                        }
                    }
                    
                    if (tableMap.containsKey("tbody") && tableMap.get("tbody") != null) {
                        for (Object tbodyObject : (List<Object>) tableMap.get("tbody")) {
                            List<String> tbodyList = new ArrayList<>();
                            for (Object tbodyValue : (List<Object>) tbodyObject) {
                                tbodyList.add(String.valueOf(tbodyValue));
                            }
                            vTable.getTbody().add(tbodyList);
                        }
                    }
                    
                    vr.tables.put(vTable.getTablename(), vTable);
                }
            }
        }
        
        return vr;
    }
    
    /**
     * 简单的JSON解析方法，替代Jackson
     */
    private static Map<String, Object> parseSimpleJson(String jsonContent) {
        Map<String, Object> result = new HashMap<>();
        
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return result;
        }
        
        // 移除首尾的大括号
        String content = jsonContent.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
        }
        
        // 简单的键值对解析
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            if (pair.contains(":")) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim();
                    
                    // 处理不同类型的值
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        // 字符串
                        result.put(key, value.substring(1, value.length() - 1));
                    } else if (value.equals("true") || value.equals("false")) {
                        // 布尔值
                        result.put(key, Boolean.valueOf(value));
                    } else if (value.equals("null")) {
                        // null值
                        result.put(key, null);
                    } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                        // 数字
                        if (value.contains(".")) {
                            result.put(key, Double.valueOf(value));
                        } else {
                            result.put(key, Long.valueOf(value));
                        }
                    } else if (value.startsWith("[") && value.endsWith("]")) {
                        // 数组
                        result.put(key, parseSimpleArray(value));
                    } else if (value.startsWith("{") && value.endsWith("}")) {
                        // 嵌套对象
                        result.put(key, parseSimpleJson(value));
                    } else {
                        // 默认作为字符串处理
                        result.put(key, value);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 解析简单数组
     */
    private static List<Object> parseSimpleArray(String arrayContent) {
        List<Object> result = new ArrayList<>();
        
        if (arrayContent == null || arrayContent.trim().isEmpty()) {
            return result;
        }
        
        // 移除首尾的方括号
        String content = arrayContent.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        
        // 简单的数组元素解析
        String[] elements = content.split(",");
        for (String element : elements) {
            String trimmedElement = element.trim();
            if (!trimmedElement.isEmpty()) {
                if (trimmedElement.startsWith("\"") && trimmedElement.endsWith("\"")) {
                    // 字符串元素
                    result.add(trimmedElement.substring(1, trimmedElement.length() - 1));
                } else if (trimmedElement.startsWith("{") && trimmedElement.endsWith("}")) {
                    // 对象元素
                    result.add(parseSimpleJson(trimmedElement));
                } else if (trimmedElement.startsWith("[") && trimmedElement.endsWith("]")) {
                    // 嵌套数组
                    result.add(parseSimpleArray(trimmedElement));
                } else {
                    // 其他类型
                    result.add(trimmedElement);
                }
            }
        }
        
        return result;
    }
}