package other.coloplast.impl.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Event_Log__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.http.CommonData;
import com.rkhd.platform.sdk.http.CommonHttpClient;
import com.rkhd.platform.sdk.http.HttpResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.service.XoqlService;

import java.util.*;

public class CommoninterfaceUtil {

    /**
     * 字段映射配置类
     */
    public static class FieldMapping {
        public String mappingParentObject; // 外部上级对象名，对应mapping_Parent_Object__c__c
        public String mappingObject; // 外部对象名
        public String mappingField;  // 外部字段名
        public String mappingFieldType; // 外部字段类型（用于判断是否为List）
        public String mappingFieldFormat; // 外部字段格式，对应mapping_Field_Format__c
        public String mappingValue;  // 映射值，对应mapping_Value__c
        public String sfdcParentObject; // 系统上级对象名，对应sFDC_Parent_Object__c__c
        public String sfdcObject;    // 系统对象名
        public String sfdcField;     // 系统字段名
        public String sfdcFieldType; // 字段类型
        public String sfdcDetailObject; // 新增，明细对象名
        public String sfdcDetailObjectWhere; // 新增，明细对象WHERE条件

        // 获取接口完整路径
        public String getFullMappingPath() {
            StringBuilder path = new StringBuilder();
            if (mappingParentObject != null && !mappingParentObject.isEmpty()) {
                path.append(mappingParentObject).append(".");
            }
            if (mappingObject != null && !mappingObject.isEmpty()) {
                path.append(mappingObject);
            }
            if (mappingField != null && !mappingField.isEmpty()) {
                if (path.length() > 0 && path.charAt(path.length() - 1) != '.') {
                    path.append(".");
                }
                path.append(mappingField);
            }
            return path.toString();
        }

        // 获取SFDC完整路径
        public String getFullSfdcPath() {
            StringBuilder path = new StringBuilder();
            if (sfdcParentObject != null && !sfdcParentObject.isEmpty()) {
                path.append(sfdcParentObject).append(".");
            }
            if (sfdcObject != null && !sfdcObject.isEmpty()) {
                path.append(sfdcObject);
            }
            if (sfdcField != null && !sfdcField.isEmpty()) {
                if (path.length() > 0 && path.charAt(path.length() - 1) != '.') {
                    path.append(".");
                }
                path.append(sfdcField);
            }
            return path.toString();
        }

        // 获取销售易风格的字段路径（A.B.D__c）
        public String getSxyFieldPath() {
            StringBuilder path = new StringBuilder();

            // 只有当字段名不为空时才构建路径
            if (sfdcField == null || sfdcField.trim().isEmpty()) {
                return "";
            }

            if (sfdcParentObject != null && !sfdcParentObject.trim().isEmpty()) {
                path.append(sfdcParentObject).append(".");
            }
            if (sfdcObject != null && !sfdcObject.trim().isEmpty()) {
                path.append(sfdcObject).append(".");
            }
            path.append(sfdcField);

            // 去除末尾多余的点
            if (path.length() > 0 && path.charAt(path.length() - 1) == '.') {
                path.deleteCharAt(path.length() - 1);
            }

            // 如果路径为空或只有点，返回空字符串
            if (path.length() == 0 || path.toString().equals(".")) {
                return "";
            }

            return path.toString();
        }
    }

    /**
     * 接口参数配置类
     */
    public static class InterfaceConfig {
        public Long id;
        public String uniqueApiName;
        public String targetObject;
        public String httpMethod;
        public String tranCode;
        public String vsid;
        public String interfaceActionUrl; // 接口URL
        public String apiToken; // API Token
        public Map<String, String> httpHeaders; // HTTP头信息
        public Integer httpTimeout; // 超时时间
        public String objectListType; // 新增：对象列表类型（Object_List__c字段）
        public String successFlag;//接口成功标识
        // 可根据实际表结构继续补充字段
    }

    /**
     * 递归获取嵌套字段值，支持多层和数组
     */
    public static Object getNestedField(Object data, String fieldPath) {
        String[] parts = fieldPath.split("\\.", 2);
        if (data instanceof JSONObject) {
            Object value = ((JSONObject) data).get(parts[0]);
            if (parts.length == 1) {
                return value;
            } else {
                return getNestedField(value, parts[1]);
            }
        } else if (data instanceof JSONArray) {
            List<Object> result = new ArrayList<>();
            for (Object item : (JSONArray) data) {
                result.add(getNestedField(item, fieldPath));
            }
            return result;
        }
        return null;
    }

    /**
     * 自动映射外部数据到系统字段
     * @param jsonData 外部数据（如接口json.txt解析后的JSONObject）
     * @param mappings 字段映射配置
     * @return 系统字段Map，所有值都包装在List中
     */
    public static Map<String, List<Object>> mapToSfdcFields(JSONObject jsonData, List<FieldMapping> mappings) {
        Map<String, Map<String, Object>> objectMap = new HashMap<>();
        Map<String, Object> listTypeMap = new HashMap<>(); // 用于存放 List 类型对象
        Map<String, String> listParentMap = new HashMap<>(); // 记录List对象的父对象，便于嵌套到父对象下
        Set<String> handledListObjects = new HashSet<>(); // 记录已处理的 List 类型对象，避免重复

        // 第一步：识别并处理所有List类型的映射
        for (FieldMapping mapping : mappings) {
            // 识别 List 类型：只有明确标记为List类型或mappingFieldType为List时才识别为List
            boolean isListType =
                    (mapping.sfdcFieldType != null && "List".equalsIgnoreCase(mapping.sfdcFieldType)) ||
                            (mapping.mappingFieldType != null && "List".equalsIgnoreCase(mapping.mappingFieldType)) ||
                            // 特殊处理：当mappingField是对象名且sfdcField为空时，认为是List类型
                            (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty() &&
                                    mapping.sfdcField != null && mapping.sfdcField.trim().isEmpty() &&
                                    mapping.sfdcDetailObject != null && !mapping.sfdcDetailObject.trim().isEmpty());

            // 特殊处理：当所有字段都映射到同一个SFDC对象且没有明确的List配置时，
            // 检查是否存在jsondata数组，如果有则认为是List类型
            if (!isListType && mapping.sfdcObject != null && !mapping.sfdcObject.trim().isEmpty()) {
                // 检查是否有其他字段也映射到同一个SFDC对象
                boolean hasMultipleFieldsForSameObject = mappings.stream()
                        .anyMatch(m -> m != mapping &&
                                m.sfdcObject != null && m.sfdcObject.equals(mapping.sfdcObject) &&
                                m.mappingField != null && !m.mappingField.trim().isEmpty());

                if (hasMultipleFieldsForSameObject && jsonData.containsKey("jsondata")) {
                    Object jsondataValue = jsonData.get("jsondata");
                    if (jsondataValue instanceof JSONArray || jsondataValue instanceof List) {
                        isListType = true;
                        // 临时设置mappingObject为jsondata
                        if (mapping.mappingObject == null || mapping.mappingObject.trim().isEmpty()) {
                            mapping.mappingObject = "jsondata";
                        }
                    }
                }
            }

            if (isListType) {
                // 列表对象名优先使用sfdcObject，其次使用配置的明细对象
                String listObjectName = (mapping.sfdcObject != null && !mapping.sfdcObject.trim().isEmpty())
                        ? mapping.sfdcObject
                        : (mapping.sfdcDetailObject != null && !mapping.sfdcDetailObject.trim().isEmpty() ? mapping.sfdcDetailObject : "default");

                System.out.println("=== 处理List类型映射 ===");
                System.out.println("mapping.mappingParentObject: " + mapping.mappingParentObject);
                System.out.println("mapping.mappingObject: " + mapping.mappingObject);
                System.out.println("mapping.mappingField: " + mapping.mappingField);
                System.out.println("mapping.mappingFieldType: " + mapping.mappingFieldType);
                System.out.println("mapping.sfdcDetailObject: " + mapping.sfdcDetailObject);
                System.out.println("mapping.sfdcObject: " + mapping.sfdcObject);
                System.out.println("listObjectName: " + listObjectName);

                if (handledListObjects.contains(listObjectName)) {
                    System.out.println("跳过已处理的List对象: " + listObjectName);
                    continue;
                }

                // 构建容器路径：处理主子表结构
                String containerPath = "";
                if (mapping.mappingParentObject != null && !mapping.mappingParentObject.trim().isEmpty()) {
                    // 如果有父对象，构建完整路径
                    if (mapping.mappingObject != null && !mapping.mappingObject.trim().isEmpty()) {
                        containerPath = mapping.mappingParentObject + "." + mapping.mappingObject;
                    } else {
                        containerPath = mapping.mappingParentObject;
                    }
                } else if (mapping.mappingObject != null && !mapping.mappingObject.trim().isEmpty()) {
                    // 只有对象名，没有父对象
                    containerPath = mapping.mappingObject;
                }

                // 特殊处理：如果容器路径为空但存在jsondata，使用jsondata作为容器
                if ((containerPath == null || containerPath.isEmpty()) && jsonData.containsKey("jsondata")) {
                    containerPath = "jsondata";
                }
                // 特殊：当mappingObject为空而mappingFieldType=List时，用mappingField作为容器名
                if ((containerPath == null || containerPath.isEmpty())
                        && mapping.mappingFieldType != null && "List".equalsIgnoreCase(mapping.mappingFieldType)
                        && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                    if (mapping.mappingParentObject != null && !mapping.mappingParentObject.trim().isEmpty()) {
                        containerPath = mapping.mappingParentObject + "." + mapping.mappingField;
                    } else {
                        containerPath = mapping.mappingField;
                    }
                }
                // 新增：当已有容器路径且为List并提供了mappingField时，将mappingField作为子路径追加
                if (mapping.mappingFieldType != null && "List".equalsIgnoreCase(mapping.mappingFieldType)
                        && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                    if (containerPath != null && !containerPath.isEmpty()) {
                        if (!containerPath.endsWith("." + mapping.mappingField) && !containerPath.equals(mapping.mappingField)) {
                            containerPath = containerPath + "." + mapping.mappingField;
                        }
                    }
                }

                System.out.println("容器路径: " + containerPath);
                Object value = getNestedField(jsonData, containerPath);
                // 回退1：当外部数据是扁平结构（如 LINES 在根上），直接按字段名取
                if (value == null && mapping.mappingFieldType != null && "List".equalsIgnoreCase(mapping.mappingFieldType)
                        && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                    value = getNestedField(jsonData, mapping.mappingField);
                }
                // 回退2：有映射对象但无父对象时，再尝试 "对象.字段" 一次（兼容不同构造流程）
                if (value == null && (mapping.mappingParentObject == null || mapping.mappingParentObject.trim().isEmpty())
                        && mapping.mappingObject != null && !mapping.mappingObject.trim().isEmpty()
                        && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                    String altPath = mapping.mappingObject + "." + mapping.mappingField;
                    if (!altPath.equals(containerPath)) {
                        value = getNestedField(jsonData, altPath);
                    }
                }
                System.out.println("获取到的值类型: " + (value != null ? value.getClass().getSimpleName() : "null"));
                if (value instanceof JSONArray) {
                    System.out.println("数组大小: " + ((JSONArray) value).size());
                }

                // 处理数组数据
                JSONArray arr = null;
                if (value instanceof JSONArray) {
                    arr = (JSONArray) value;
                } else if (value instanceof List) {
                    arr = new JSONArray();
                    for (Object o : (List<?>) value) {
                        if (o instanceof JSONObject) {
                            arr.add((JSONObject) o);
                        } else if (o instanceof Map) {
                            arr.add(new JSONObject((Map<String, Object>) o));
                        }
                    }
                }

                if (arr != null && !arr.isEmpty()) {
                    JSONArray resultArr = new JSONArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        JSONObject mappedItem = new JSONObject();

                        // 查找所有属于这个明细对象的字段映射
                        for (FieldMapping subMapping : mappings) {
                            // 匹配条件：属于相同的mappingObject或list字段名作为容器，且映射到相同的明细对象
                            // 通用多层级数据匹配逻辑
                            boolean isSameMappingObject = isMappingObjectMatch(mapping, subMapping);

                            // 关键修复：明细表字段的匹配逻辑
                            boolean isSameSfdcObject = isSfdcObjectMatch(listObjectName, subMapping);

                            // 排除List类型字段，只处理普通字段
                            boolean isNotListType = subMapping.sfdcFieldType == null || !"List".equalsIgnoreCase(subMapping.sfdcFieldType);

                            // 确保有有效的映射字段
                            boolean hasValidMappingField = subMapping.mappingField != null && !subMapping.mappingField.trim().isEmpty();


                            if (isSameMappingObject && isSameSfdcObject && isNotListType && hasValidMappingField) {
                                Object v = item.get(subMapping.mappingField);
                                // 日期/时间类型转换为时间戳
                                if (v != null && ("Date".equalsIgnoreCase(subMapping.sfdcFieldType) || "Datetime".equalsIgnoreCase(subMapping.sfdcFieldType))) {
                                    if (v instanceof String) {
                                        long ts = TimestampConverter.toTimestamp((String) v);
                                        if (ts != -1) {
                                            v = ts;
                                        }else {
                                            v = null;
                                        }
                                    }
                                }
                                if (v != null) {
                                    mappedItem.put(subMapping.sfdcField, v);
                                    System.out.println("成功映射字段: " + subMapping.mappingField + " -> " + subMapping.sfdcField + " = " + v);
                                }
                            }
                        }

                        // 处理嵌套对象字段映射
                        for (FieldMapping subMapping : mappings) {
                            // 检查是否是嵌套对象字段
                            boolean isNestedObjectField = subMapping.sfdcDetailObject != null && !subMapping.sfdcDetailObject.trim().isEmpty() &&
                                    subMapping.sfdcParentObject != null && !subMapping.sfdcParentObject.trim().isEmpty() &&
                                    subMapping.sfdcParentObject.equals(listObjectName);

                            if (isNestedObjectField && subMapping.mappingField != null && !subMapping.mappingField.trim().isEmpty()) {
                                Object v = item.get(subMapping.mappingField);
                                if (v != null) {
                                    // 创建或获取嵌套对象
                                    Map<String, Object> nestedObj = (Map<String, Object>) mappedItem.get(subMapping.sfdcDetailObject);
                                    if (nestedObj == null) {
                                        nestedObj = new HashMap<>();
                                        mappedItem.put(subMapping.sfdcDetailObject, nestedObj);
                                    }
                                    nestedObj.put(subMapping.sfdcField, v);
                                    System.out.println("成功映射嵌套字段: " + subMapping.mappingField + " -> " + subMapping.sfdcParentObject + "." + subMapping.sfdcDetailObject + "." + subMapping.sfdcField + " = " + v);
                                }
                            }
                        }

                        // 特殊处理：对子表中的计划项 ORDERPLANITEM__C 先按映射转换再序列化到 detailJson__c
                        try {
                            Object planObj = null;
                            if (item.containsKey("ORDERPLANITEM__C")) {
                                planObj = item.get("ORDERPLANITEM__C");
                            } else if (item.containsKey("ORDERPLANITEM_C")) {
                                planObj = item.get("ORDERPLANITEM_C");
                            }
                            if (planObj instanceof JSONArray) {
                                JSONArray planArr = (JSONArray) planObj;
                                JSONArray mappedPlanArr = new JSONArray();

                                // 识别计划项在SFDC中的对象名：通常为 orderPlanItem__c
                                String planSfdcObjectName = "orderPlanItem__c";

                                for (int j = 0; j < planArr.size(); j++) {
                                    JSONObject planItem = planArr.getJSONObject(j);
                                    JSONObject mappedPlanItem = new JSONObject();
                                    for (FieldMapping fmPlan : mappings) {
                                        boolean isPlanField = isSfdcObjectMatch(planSfdcObjectName, fmPlan)
                                                && (fmPlan.sfdcFieldType == null || !"List".equalsIgnoreCase(fmPlan.sfdcFieldType))
                                                && fmPlan.mappingField != null && !fmPlan.mappingField.trim().isEmpty();
                                        if (isPlanField) {
                                            Object v = planItem.get(fmPlan.mappingField);
                                            if (v != null && ("Date".equalsIgnoreCase(fmPlan.sfdcFieldType) || "Datetime".equalsIgnoreCase(fmPlan.sfdcFieldType))) {
                                                if (v instanceof String) {
                                                    long ts = TimestampConverter.toTimestamp((String) v);
                                                    if (ts != -1) {
                                                        v = ts;
                                                    }else {
                                                        v = null;
                                                    }
                                                }
                                            }
                                            if (v != null) {
                                                mappedPlanItem.put(fmPlan.sfdcField, v);
                                            }
                                        }
                                    }
                                    if (!mappedPlanItem.isEmpty()) {
                                        mappedPlanArr.add(mappedPlanItem);
                                    }
                                }

                                if (!mappedPlanArr.isEmpty()) {
                                    // 以数组形式存储，不再二次序列化为字符串
                                    mappedItem.put("detailJson__c", mappedPlanArr);
                                }
                            }
                        } catch (Exception e) {
                            // 静默忽略计划项的映射异常，避免影响主映射
                        }

                        // 只有当映射项有内容时才添加
                        if (!mappedItem.isEmpty()) {
                            resultArr.add(mappedItem);
                        }
                    }

                    if (!resultArr.isEmpty()) {
                        listTypeMap.put(listObjectName, resultArr);
                        // 记录父对象（若配置了）
                        if (mapping.sfdcParentObject != null && !mapping.sfdcParentObject.trim().isEmpty()) {
                            listParentMap.put(listObjectName, mapping.sfdcParentObject);
                        } else {
                            // 智能推断父对象：根据外部父对象(mappingParentObject)反查其SFDC对象
                            String inferredParent = null;
                            if (mapping.mappingParentObject != null && !mapping.mappingParentObject.trim().isEmpty()) {
                                for (FieldMapping pm : mappings) {
                                    if (mapping.mappingParentObject.equals(pm.mappingObject)
                                            && pm.sfdcObject != null && !pm.sfdcObject.trim().isEmpty()) {
                                        inferredParent = pm.sfdcObject;
                                        break;
                                    }
                                }
                            }
                            // 进一步：若父对象仍无法推断，尝试通过“父对象.List字段名”的容器关系匹配
                            if (inferredParent == null && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                                for (FieldMapping pm : mappings) {
                                    boolean isParentContainer =
                                            pm.mappingObject != null && pm.mappingField != null &&
                                                    pm.mappingFieldType != null && "List".equalsIgnoreCase(pm.mappingFieldType) &&
                                                    mapping.mappingField.equals(pm.mappingField) &&
                                                    pm.sfdcObject != null && !pm.sfdcObject.trim().isEmpty();
                                    if (isParentContainer) {
                                        inferredParent = pm.sfdcObject;
                                        break;
                                    }
                                }
                            }
                            if (inferredParent != null && !inferredParent.trim().isEmpty()) {
                                listParentMap.put(listObjectName, inferredParent);
                            }
                        }
                        handledListObjects.add(listObjectName);
                    }
                } else {
                    // 无论是否有数据，都保持结构一致：为父对象下挂空数组
                    System.out.println("未找到明细数据，为保持结构创建空列表: " + listObjectName);
                    JSONArray resultArr = new JSONArray();
                    listTypeMap.put(listObjectName, resultArr);

                    // 记录父对象（若配置了）或进行智能推断，保证即使为空也能挂在父对象下
                    if (mapping.sfdcParentObject != null && !mapping.sfdcParentObject.trim().isEmpty()) {
                        listParentMap.put(listObjectName, mapping.sfdcParentObject);
                    } else {
                        // 智能推断父对象：根据外部父对象(mappingParentObject)反查其SFDC对象
                        String inferredParent = null;
                        if (mapping.mappingParentObject != null && !mapping.mappingParentObject.trim().isEmpty()) {
                            for (FieldMapping pm : mappings) {
                                if (mapping.mappingParentObject.equals(pm.mappingObject)
                                        && pm.sfdcObject != null && !pm.sfdcObject.trim().isEmpty()) {
                                    inferredParent = pm.sfdcObject;
                                    break;
                                }
                            }
                        }
                        // 进一步：若父对象仍无法推断，尝试通过“父对象.List字段名”的容器关系匹配
                        if (inferredParent == null && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                            for (FieldMapping pm : mappings) {
                                boolean isParentContainer =
                                        pm.mappingObject != null && pm.mappingField != null &&
                                                pm.mappingFieldType != null && "List".equalsIgnoreCase(pm.mappingFieldType) &&
                                                mapping.mappingField.equals(pm.mappingField) &&
                                                pm.sfdcObject != null && !pm.sfdcObject.trim().isEmpty();
                                if (isParentContainer) {
                                    inferredParent = pm.sfdcObject;
                                    break;
                                }
                            }
                        }
                        if (inferredParent != null && !inferredParent.trim().isEmpty()) {
                            listParentMap.put(listObjectName, inferredParent);
                        }
                    }
                    handledListObjects.add(listObjectName);
                }
            }
        }

        // 第二步：处理普通字段映射
        for (FieldMapping mapping : mappings) {
            // 跳过已经处理的List类型字段和嵌套对象字段
            boolean isListType =
                    (mapping.sfdcFieldType != null && "List".equalsIgnoreCase(mapping.sfdcFieldType)) ||
                            (mapping.sfdcDetailObject != null && !mapping.sfdcDetailObject.trim().isEmpty() &&
                                    mapping.sfdcFieldType != null && "List".equalsIgnoreCase(mapping.sfdcFieldType));

            // 跳过嵌套对象字段（这些字段需要在List处理中处理）
            boolean isNestedObjectField = mapping.sfdcDetailObject != null && !mapping.sfdcDetailObject.trim().isEmpty() &&
                    mapping.sfdcParentObject != null && !mapping.sfdcParentObject.trim().isEmpty();

            if (isListType || isNestedObjectField) {
                continue;
            }

            String fieldPath = mapping.getFullMappingPath();
            String sfdcObject =
                    (mapping.sfdcObject != null && !mapping.sfdcObject.trim().isEmpty())
                            ? mapping.sfdcObject
                            : ((mapping.sfdcParentObject != null && !mapping.sfdcParentObject.trim().isEmpty())
                            ? mapping.sfdcParentObject
                            : "default");

            // 普通字段处理
            Object value = getNestedField(jsonData, fieldPath);
            // 兼容扁平结构：当如 Bid__c.Name 这类路径在数据中不存在时，回退直接取字段名（例如 Name）
            if (value == null) {
                String fallbackField = mapping.mappingField;
                if (fallbackField != null && !fallbackField.trim().isEmpty()) {
                    Object flatVal = getNestedField(jsonData, fallbackField);
                    if (flatVal != null) {
                        value = flatVal;
                    }
                }
            }
            // 再次兼容：当mappingObject非空但父对象为空，尝试 "mappingObject.mappingField"
            if (value == null && (mapping.mappingParentObject == null || mapping.mappingParentObject.trim().isEmpty())
                    && mapping.mappingObject != null && !mapping.mappingObject.trim().isEmpty()
                    && mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                String altPath = mapping.mappingObject + "." + mapping.mappingField;
                value = getNestedField(jsonData, altPath);
            }

            // 处理数组格式的数据：如果返回的是List且包含对象，取第一个元素
            if (value instanceof List && !((List<?>) value).isEmpty()) {
                List<?> list = (List<?>) value;
                Object firstItem = list.get(0);
                if (firstItem instanceof JSONObject || firstItem instanceof Map) {
                    // 如果第一个元素是对象，直接使用
                    value = firstItem;
                } else {
                    // 如果第一个元素是基本类型，取第一个值
                    value = firstItem;
                }
            }

            // 日期/时间类型转换为时间戳
            if (value != null && ("Date".equalsIgnoreCase(mapping.sfdcFieldType) || "Datetime".equalsIgnoreCase(mapping.sfdcFieldType))) {
                if (value instanceof String) {
                    long ts = TimestampConverter.toTimestamp((String) value);
                    if (ts != -1) {
                        value = ts;
                    }else {
                        value = null;
                    }
                }
            }

            // 普通字段映射
            objectMap.computeIfAbsent(sfdcObject, k -> new HashMap<>()).put(mapping.sfdcField, value);
        }

        // 第三步：组装最终结果
        Map<String, List<Object>> result = new HashMap<>();

        // 先放普通对象
        for (Map.Entry<String, Map<String, Object>> entry : objectMap.entrySet()) {
            // 如果 List 类型已处理，则跳过
            if (listTypeMap.containsKey(entry.getKey())) continue;
            // 将普通对象包装在List中
            List<Object> wrappedValue = new ArrayList<>();
            wrappedValue.add(entry.getValue());
            result.put(entry.getKey(), wrappedValue);
        }

        // 再放 List 类型对象：优先嵌套到父对象下
        for (Map.Entry<String, Object> entry : listTypeMap.entrySet()) {
            String listName = entry.getKey();
            Object listValue = entry.getValue();
            String parentName = listParentMap.get(listName);

            if (parentName != null && !parentName.trim().isEmpty()) {
                // 获取或创建父对象的List
                List<Object> parentList = result.get(parentName);
                if (parentList == null || parentList.isEmpty()) {
                    parentList = new ArrayList<>();
                    result.put(parentName, parentList);
                }

                // 如果父对象List中的第一个元素不是Map，创建一个Map
                if (parentList.isEmpty() || !(parentList.get(0) instanceof Map)) {
                    Map<String, Object> parentObj = new HashMap<>();
                    parentList.add(parentObj);
                }

                // 将List值包装在List中并添加到父对象
                List<Object> wrappedListValue = new ArrayList<>();
                if (listValue instanceof List) {
                    wrappedListValue.addAll((List<?>) listValue);
                } else {
                    wrappedListValue.add(listValue);
                }
                ((Map<String, Object>) parentList.get(0)).put(listName, wrappedListValue);
            } else {
                // 无父对象配置，保持顶层，将值包装在List中
                List<Object> wrappedValue = new ArrayList<>();
                if (listValue instanceof List) {
                    wrappedValue.addAll((List<?>) listValue);
                } else {
                    wrappedValue.add(listValue);
                }
                result.put(listName, wrappedValue);
            }
        }

        System.out.println("字段映射配置:"+result);
        return result;
    }

    /**
     * 反向映射：将系统字段Map转换为外部字段结构
     * @param sfdcData 系统字段Map（如autoMapping的返回值）
     * @param mappings 字段映射配置
     * @return 外部字段结构（JSONObject）
     */
    public static JSONObject mapToExternalFields(Map<String, Object> sfdcData, List<FieldMapping> mappings) {
        JSONObject result = new JSONObject();
        for (FieldMapping mapping : mappings) {
            String sfdcObject = mapping.sfdcObject != null ? mapping.sfdcObject : "default";
            Map<String, Object> objectData = null;
            Object obj = sfdcData.get(sfdcObject);
            if (obj instanceof Map) {
                objectData = (Map<String, Object>) obj;
            } else if (obj instanceof JSONObject) {
                objectData = ((JSONObject) obj);
            }
            if (objectData == null) continue;
            Object value = objectData.get(mapping.sfdcField);
            if (value == null) continue;

            if (mapping.mappingObject == null || mapping.mappingObject.isEmpty()) {
                result.put(mapping.mappingField, value);
            } else {
                JSONObject subObj = result.getJSONObject(mapping.mappingObject);
                if (subObj == null) {
                    subObj = new JSONObject();
                    result.put(mapping.mappingObject, subObj);
                }
                subObj.put(mapping.mappingField, value);
            }
        }
        return result;
    }

    /**
     * 递归自动映射主表、子表（List）、嵌套对象（Object）
     * @param data 当前数据（JSONObject）
     * @param mappings 字段映射配置
     * @param valueMapping 字段值映射配置
     * @return JSONObject（可嵌套/含List）
     */
    public static Object mapToExternalFieldsRecursive(Object data, List<FieldMapping> mappings, Map<String, Map<String, Object>> valueMapping) {
        if (data == null) return null;

        // 处理List
        if (data instanceof JSONArray) {
            JSONArray arr = (JSONArray) data;
            JSONArray resultArr = new JSONArray();
            for (int i = 0; i < arr.size(); i++) {
                resultArr.add(mapToExternalFieldsRecursive(arr.get(i), mappings, valueMapping));
            }
            return resultArr;
        }

        // 处理Object
        if (data instanceof JSONObject) {
            JSONObject obj = (JSONObject) data;
            JSONObject result = new JSONObject();

            // 按映射对象分组处理
            Map<String, List<FieldMapping>> objectMappings = new HashMap<>();
            Map<String, String> objectFieldTypes = new HashMap<>(); // 记录每个对象的字段类型

            for (FieldMapping mapping : mappings) {
                String mappingObject = mapping.mappingObject != null ? mapping.mappingObject : "";
                objectMappings.computeIfAbsent(mappingObject, k -> new ArrayList<>()).add(mapping);

                // 记录对象的字段类型（用于后续判断是否为List类型）
                if (mapping.mappingFieldType != null && !mapping.mappingFieldType.isEmpty()) {
                    objectFieldTypes.put(mappingObject, mapping.mappingFieldType);
                }
            }



            // 处理每个映射对象
            for (Map.Entry<String, List<FieldMapping>> entry : objectMappings.entrySet()) {
                String mappingObject = entry.getKey();
                List<FieldMapping> objectMappingList = entry.getValue();

                if (mappingObject.isEmpty()) {
                    // MappingObject为空时，使用MappingField作为key，根据MappingFieldType进行拼接

                    // 检查字段类型并分别处理
                    for (FieldMapping mapping : objectMappingList) {
                        if (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                            if ("List".equalsIgnoreCase(mapping.mappingFieldType)) {
                                // 处理List类型字段：需要收集所有相关字段并聚合为对象数组
                                String listFieldName = mapping.mappingField;

                                // 查找所有属于这个List对象的子字段
                                JSONObject listItem = new JSONObject();
                                boolean hasValidFields = false;

                                for (FieldMapping subMapping : mappings) {
                                    if (listFieldName.equals(subMapping.mappingObject) &&
                                            subMapping.mappingField != null && !subMapping.mappingField.trim().isEmpty()) {

                                        Object value;
                                        // 如果mappingValue不为空就赋值mappingValue反之正常赋值sfdcField
                                        if (subMapping.mappingValue != null && !subMapping.mappingValue.trim().isEmpty()) {
                                            value = subMapping.mappingValue;
                                        } else {
                                            // 使用getOrDefault确保字段不存在时返回空字符串
                                            value = obj.getOrDefault(subMapping.sfdcField, "");
                                        }

                                        // 处理日期类型转换
                                        if (value != null && ("Date".equalsIgnoreCase(subMapping.sfdcFieldType) ||
                                                "Datetime".equalsIgnoreCase(subMapping.sfdcFieldType))) {
                                            // 优先使用mappingFieldFormat，如果为空则根据mappingFieldType决定日期格式
                                            String dateFormat;
                                            if (subMapping.mappingFieldFormat != null && !subMapping.mappingFieldFormat.trim().isEmpty()) {
                                                dateFormat = subMapping.mappingFieldFormat;
                                            } else {
                                                dateFormat = "Date".equalsIgnoreCase(subMapping.mappingFieldType) ?
                                                        "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
                                            }
                                            if (value instanceof Long) {
                                                value = new java.text.SimpleDateFormat(dateFormat).format(new Date((Long) value));
                                            } else if (value instanceof String) {
                                                try {
                                                    Long timestamp = Long.parseLong((String) value);
                                                    value = new java.text.SimpleDateFormat(dateFormat).format(new Date(timestamp));
                                                } catch (NumberFormatException e) {
                                                    // 如果不是数字，保持原值
                                                }
                                            }
                                        }
                                        if (value != null && "Boolean".equalsIgnoreCase(subMapping.sfdcFieldType)) {
                                            System.out.println("Boolean1:"+value);
                                            if (value instanceof Number) {
                                                value = ((Number) value).intValue() == 1;
                                            } else if (value instanceof String) {
                                                String strValue = ((String) value).trim();
                                                value = "1".equals(strValue);
                                            }
                                        }
                                        // 处理SQL查询返回的数组值（如果字段配置为String类型）
                                        if ("String".equalsIgnoreCase(subMapping.sfdcFieldType) && value instanceof JSONArray) {
                                            JSONArray array = (JSONArray) value;
                                            if (array.size() > 0) {
                                                StringBuilder stringBuilder = new StringBuilder();
                                                for (Object o : array) {
                                                    stringBuilder.append(o.toString());
                                                    stringBuilder.append(";");
                                                }
                                                value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                            }
//                                            else {
//                                                value = "";
//                                            }
                                        }

                                        // 处理值映射
                                        if (valueMapping.containsKey(subMapping.mappingField) && value != null) {
                                            Object mappedVal = valueMapping.get(subMapping.mappingField).get(String.valueOf(value));
                                            if (mappedVal != null) {
                                                if ("String".equalsIgnoreCase(subMapping.sfdcFieldType) && mappedVal instanceof JSONArray) {
                                                    JSONArray array = (JSONArray) mappedVal;
                                                    if (array.size() > 0) {
                                                        StringBuilder stringBuilder = new StringBuilder();
                                                        for (Object o : array) {
                                                            stringBuilder.append(o.toString());
                                                            stringBuilder.append(";");
                                                        }
                                                        value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                                    }
//                                                    else {
//                                                        value = "";
//                                                    }
                                                } else {
                                                    value = mappedVal;
                                                }
                                            }
                                        }

                                        listItem.put(subMapping.mappingField, value);
                                        hasValidFields = true;
                                    }
                                }

                                // 将聚合的对象包装成数组
                                if (hasValidFields) {
                                    JSONArray array = new JSONArray();
                                    array.add(listItem);
                                    result.put(listFieldName, array);
                                } else {
                                    result.put(listFieldName, new JSONArray());
                                }
                            } else if ("Object".equalsIgnoreCase(mapping.mappingFieldType)) {
                                // 处理Object类型字段：需要收集所有相关字段并聚合为对象
                                String objectFieldName = mapping.mappingField;

                                // 查找所有属于这个Object对象的子字段
                                JSONObject objectItem = new JSONObject();
                                boolean hasValidFields = false;

                                for (FieldMapping subMapping : mappings) {
                                    if (objectFieldName.equals(subMapping.mappingObject) &&
                                            subMapping.mappingField != null && !subMapping.mappingField.trim().isEmpty()) {

                                        Object value;
                                        // 如果mappingValue不为空就赋值mappingValue反之正常赋值sfdcField
                                        if (subMapping.mappingValue != null && !subMapping.mappingValue.trim().isEmpty()) {
                                            value = subMapping.mappingValue;
                                        } else {
                                            // 使用getOrDefault确保字段不存在时返回空字符串
                                            value = obj.getOrDefault(subMapping.sfdcField, "");
                                        }

                                        // 处理日期类型转换
                                        if (value != null && ("Date".equalsIgnoreCase(subMapping.sfdcFieldType) ||
                                                "Datetime".equalsIgnoreCase(subMapping.sfdcFieldType))) {
                                            // 优先使用mappingFieldFormat，如果为空则根据mappingFieldType决定日期格式
                                            String dateFormat;
                                            if (subMapping.mappingFieldFormat != null && !subMapping.mappingFieldFormat.trim().isEmpty()) {
                                                dateFormat = subMapping.mappingFieldFormat;
                                            } else {
                                                dateFormat = "Date".equalsIgnoreCase(subMapping.mappingFieldType) ?
                                                        "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
                                            }
                                            if (value instanceof Long) {
                                                value = new java.text.SimpleDateFormat(dateFormat).format(new Date((Long) value));
                                            } else if (value instanceof String) {
                                                try {
                                                    Long timestamp = Long.parseLong((String) value);
                                                    value = new java.text.SimpleDateFormat(dateFormat).format(new Date(timestamp));
                                                } catch (NumberFormatException e) {
                                                    // 如果不是数字，保持原值
                                                }
                                            }
                                        }
                                        if (value != null && "Boolean".equalsIgnoreCase(subMapping.sfdcFieldType)) {
                                            System.out.println("Boolean2:"+value);
                                            if (value instanceof Number) {
                                                value = ((Number) value).intValue() == 1;
                                            } else if (value instanceof String) {
                                                String strValue = ((String) value).trim();
                                                value = "1".equals(strValue);
                                            }
                                        }
                                        // 处理SQL查询返回的数组值（如果字段配置为String类型）
                                        if ("String".equalsIgnoreCase(subMapping.sfdcFieldType) && value instanceof JSONArray) {
                                            JSONArray array = (JSONArray) value;
                                            if (array.size() > 0) {
                                                StringBuilder stringBuilder = new StringBuilder();
                                                for (Object o : array) {
                                                    stringBuilder.append(o.toString());
                                                    stringBuilder.append(";");
                                                }
                                                value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                            }
//                                            else {
//                                                value = "";
//                                            }
                                        }

                                        // 处理值映射
                                        if (valueMapping.containsKey(subMapping.mappingField) && value != null) {
                                            Object mappedVal = valueMapping.get(subMapping.mappingField).get(String.valueOf(value));
                                            if (mappedVal != null) {
                                                if ("String".equalsIgnoreCase(subMapping.sfdcFieldType) && mappedVal instanceof JSONArray) {
                                                    JSONArray array = (JSONArray) mappedVal;
                                                    if (array.size() > 0) {
                                                        StringBuilder stringBuilder = new StringBuilder();
                                                        for (Object o : array) {
                                                            stringBuilder.append(o.toString());
                                                            stringBuilder.append(";");
                                                        }
                                                        value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                                    }
//                                                    else {
//                                                        value = "";
//                                                    }
                                                } else {
                                                    value = mappedVal;
                                                }
                                            }
                                        }

                                        objectItem.put(subMapping.mappingField, value);
                                        hasValidFields = true;
                                    }
                                }

                                // 将聚合的对象放入结果
                                if (hasValidFields) {
                                    result.put(objectFieldName, objectItem);
                                }
                            } else {
                                // 处理普通字段映射（String类型或其他）
                                Object value;
                                // 如果mappingValue不为空就赋值mappingValue反之正常赋值sfdcField
                                if (mapping.mappingValue != null && !mapping.mappingValue.trim().isEmpty()) {
                                    value = mapping.mappingValue;
                                } else {
                                    // 使用getOrDefault确保字段不存在时返回空字符串
                                    value = obj.getOrDefault(mapping.sfdcField, "");
                                }

                                // 处理日期类型转换
                                if (value != null && ("Date".equalsIgnoreCase(mapping.sfdcFieldType) ||
                                        "Datetime".equalsIgnoreCase(mapping.sfdcFieldType))) {
                                    // 优先使用mappingFieldFormat，如果为空则根据mappingFieldType决定日期格式
                                    String dateFormat;
                                    if (mapping.mappingFieldFormat != null && !mapping.mappingFieldFormat.trim().isEmpty()) {
                                        dateFormat = mapping.mappingFieldFormat;
                                    } else {
                                        dateFormat = "Date".equalsIgnoreCase(mapping.mappingFieldType) ?
                                                "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
                                    }
                                    if (value instanceof Long) {
                                        value = new java.text.SimpleDateFormat(dateFormat).format(new Date((Long) value));
                                    } else if (value instanceof String) {
                                        try {
                                            Long timestamp = Long.parseLong((String) value);
                                            value = new java.text.SimpleDateFormat(dateFormat).format(new Date(timestamp));
                                        } catch (NumberFormatException e) {
                                            // 如果不是数字，保持原值
                                        }
                                    }
                                }
                                if (value != null && "Boolean".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                    System.out.println("Boolean3:"+value);
                                    if (value instanceof Number) {
                                        value = ((Number) value).intValue() == 1;
                                    } else if (value instanceof String) {
                                        String strValue = ((String) value).trim();
                                        value = "1".equals(strValue);
                                    }
                                }
                                // 处理SQL查询返回的数组值（如果字段配置为String类型）
                                if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && value instanceof JSONArray) {
                                    JSONArray array = (JSONArray) value;
                                    if (array.size() > 0) {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        for (Object o : array) {
                                            stringBuilder.append(o.toString());
                                            stringBuilder.append(";");
                                        }
                                        value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                    }
//                                    else {
//                                        value = "";
//                                    }
                                }

                                // 处理值映射
                                if (valueMapping.containsKey(mapping.mappingField) && value != null) {
                                    Object mappedVal = valueMapping.get(mapping.mappingField).get(String.valueOf(value));
                                    if (mappedVal != null) {
                                        // 检查字段类型，如果是String类型但映射值是数组，则取第一个元素
                                        if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && mappedVal instanceof JSONArray) {
                                            JSONArray array = (JSONArray) mappedVal;
                                            if (array.size() > 0) {
                                                StringBuilder stringBuilder = new StringBuilder();
                                                for (Object o : array) {
                                                    stringBuilder.append(o.toString());
                                                    stringBuilder.append(";");
                                                }
                                                value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                            }
//                                            else {
//                                                value = "";
//                                            }
                                        } else {
                                            value = mappedVal;
                                        }
                                    }
                                }

                                result.put(mapping.mappingField, value);
                            }
                        }
                    }
                } else {
                    // 检查这个对象是否已经被List类型字段处理过了
                    boolean isProcessedByList = false;
                    for (String listFieldName : result.keySet()) {
                        if (mappingObject.equals(listFieldName)) {
                            isProcessedByList = true;
                            break;
                        }
                    }

                    // 如果已经被List类型字段处理过了，跳过
                    if (isProcessedByList) {
                        continue;
                    }

                    // 检查是否有List类型的字段映射
                    boolean hasListTypeMapping = false;
                    String objectFieldType = objectFieldTypes.get(mappingObject);

                    // 检查对象本身的字段类型是否为List
                    if ("List".equalsIgnoreCase(objectFieldType)) {
                        hasListTypeMapping = true;
                    } else {
                        // 检查字段级别的List类型
                        for (FieldMapping mapping : objectMappingList) {
                            if ("List".equalsIgnoreCase(mapping.sfdcFieldType) || "List".equalsIgnoreCase(mapping.mappingFieldType)) {
                                hasListTypeMapping = true;
                                break;
                            }
                        }
                    }

                    if (hasListTypeMapping) {
                        // 处理List类型字段
                        // 优先从主查询结果中的子查询数组读取明细数据（key形如：SELECT ... FROM <detailObj> ...）
                        String detailObjName = null;
                        for (FieldMapping m : objectMappingList) {
                            if (m.sfdcDetailObject != null && !m.sfdcDetailObject.trim().isEmpty()) {
                                detailObjName = m.sfdcDetailObject;
                                break;
                            }
                        }

                        JSONArray detailArray = null;
                        if (detailObjName != null) {
                            detailArray = findDetailArray(obj, detailObjName);
                        }

                        if (detailArray != null && !detailArray.isEmpty()) {
                            JSONArray mappedArray = new JSONArray();
                            for (int i = 0; i < detailArray.size(); i++) {
                                JSONObject child = detailArray.getJSONObject(i);
                                JSONObject mappedItem = new JSONObject();
                                boolean hasValidFields = false;

                                for (FieldMapping mapping : objectMappingList) {
                                    if (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                                        Object value;
                                        if (mapping.mappingValue != null && !mapping.mappingValue.trim().isEmpty()) {
                                            value = mapping.mappingValue;
                                        } else {
                                            value = child.getOrDefault(mapping.sfdcField, "");
                                        }

                                        // 日期/时间转换
                                        if (value != null && ("Date".equalsIgnoreCase(mapping.sfdcFieldType) ||
                                                "Datetime".equalsIgnoreCase(mapping.sfdcFieldType))) {
                                            String dateFormat = "Date".equalsIgnoreCase(mapping.mappingFieldType) ?
                                                    "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
                                            if (value instanceof Long) {
                                                value = new java.text.SimpleDateFormat(dateFormat).format(new Date((Long) value));
                                            } else if (value instanceof String) {
                                                try {
                                                    Long timestamp = Long.parseLong((String) value);
                                                    value = new java.text.SimpleDateFormat(dateFormat).format(new Date(timestamp));
                                                } catch (NumberFormatException e) {
                                                }
                                            }
                                        }
                                        if (value != null && "Boolean".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                            if (value instanceof Number) {
                                                value = ((Number) value).intValue() == 1;
                                            } else if (value instanceof String) {
                                                String strValue = ((String) value).trim();
                                                value = "1".equals(strValue);
                                            }
                                        }
                                        if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && value instanceof JSONArray) {
                                            JSONArray array = (JSONArray) value;
                                            StringBuilder stringBuilder = new StringBuilder();
                                            for (Object o : array) {
                                                stringBuilder.append(o.toString());
                                                stringBuilder.append(";");
                                            }
                                            value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                        }
                                        if (valueMapping.containsKey(mapping.mappingField) && value != null) {
                                            Object mappedVal = valueMapping.get(mapping.mappingField).get(String.valueOf(value));
                                            if (mappedVal != null) {
                                                if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && mappedVal instanceof JSONArray) {
                                                    JSONArray array = (JSONArray) mappedVal;
                                                    StringBuilder stringBuilder = new StringBuilder();
                                                    for (Object o : array) {
                                                        stringBuilder.append(o.toString());
                                                        stringBuilder.append(";");
                                                    }
                                                    value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                                } else {
                                                    value = mappedVal;
                                                }
                                            }
                                        }
                                        mappedItem.put(mapping.mappingField, value);
                                        hasValidFields = true;
                                    }
                                }

                                if (hasValidFields) {
                                    mappedArray.add(mappedItem);
                                }
                            }

                            result.put(mappingObject, mappedArray);
                        } else {
                            // 回退：将主查询的扁平数据聚合为对象数组（保持原有逻辑）
                            JSONObject mappedItem = new JSONObject();
                            boolean hasValidFields = false;
                            for (FieldMapping mapping : objectMappingList) {
                                if (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                                    Object value;
                                    if (mapping.mappingValue != null && !mapping.mappingValue.trim().isEmpty()) {
                                        value = mapping.mappingValue;
                                    } else {
                                        value = obj.getOrDefault(mapping.sfdcField, "");
                                    }
                                    if (value != null && ("Date".equalsIgnoreCase(mapping.sfdcFieldType) ||
                                            "Datetime".equalsIgnoreCase(mapping.sfdcFieldType))) {
                                        String dateFormat = "Date".equalsIgnoreCase(mapping.mappingFieldType) ?
                                                "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
                                        if (value instanceof Long) {
                                            value = new java.text.SimpleDateFormat(dateFormat).format(new Date((Long) value));
                                        } else if (value instanceof String) {
                                            try {
                                                Long timestamp = Long.parseLong((String) value);
                                                value = new java.text.SimpleDateFormat(dateFormat).format(new Date(timestamp));
                                            } catch (NumberFormatException e) {
                                            }
                                        }
                                    }
                                    if (value != null && "Boolean".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                        System.out.println("Boolean4:"+value);
                                        if (value instanceof Number) {
                                            value = ((Number) value).intValue() == 1;
                                        } else if (value instanceof String) {
                                            String strValue = ((String) value).trim();
                                            value = "1".equals(strValue);
                                        }
                                    }
                                    if (value != null) {
                                        if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && value instanceof JSONArray) {
                                            JSONArray array = (JSONArray) value;
                                            StringBuilder stringBuilder = new StringBuilder();
                                            for (Object o : array) {
                                                stringBuilder.append(o.toString());
                                                stringBuilder.append(";");
                                            }
                                            value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                        }
                                        if (valueMapping.containsKey(mapping.mappingField)) {
                                            Object mappedVal = valueMapping.get(mapping.mappingField).get(String.valueOf(value));
                                            if (mappedVal != null) {
                                                if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && mappedVal instanceof JSONArray) {
                                                    JSONArray array = (JSONArray) mappedVal;
                                                    StringBuilder stringBuilder = new StringBuilder();
                                                    for (Object o : array) {
                                                        stringBuilder.append(o.toString());
                                                        stringBuilder.append(";");
                                                    }
                                                    value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                                } else {
                                                    value = mappedVal;
                                                }
                                            }
                                        }
                                        mappedItem.put(mapping.mappingField, value);
                                        hasValidFields = true;
                                    }
                                }
                            }

                            if (hasValidFields) {
                                JSONArray array = new JSONArray();
                                array.add(mappedItem);
                                result.put(mappingObject, array);
                            } else {
                                result.put(mappingObject, new JSONArray());
                            }
                        }


                    } else {
                        // 嵌套对象映射（非List类型）
                        JSONObject nestedObj = new JSONObject();
                        boolean hasValidFields = false;

                        for (FieldMapping mapping : objectMappingList) {
                            if (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                                Object value;
                                // 如果mappingValue不为空就赋值mappingValue反之正常赋值sfdcField
                                if (mapping.mappingValue != null && !mapping.mappingValue.trim().isEmpty()) {
                                    value = mapping.mappingValue;
                                } else {
                                    // 使用getOrDefault确保字段不存在时返回空字符串
                                    value = obj.getOrDefault(mapping.sfdcField, "");
                                }

                                // 处理日期类型转换
                                if (value != null && ("Date".equalsIgnoreCase(mapping.sfdcFieldType) ||
                                        "Datetime".equalsIgnoreCase(mapping.sfdcFieldType))) {
                                    // 根据mappingFieldType决定日期格式
                                    String dateFormat = "Date".equalsIgnoreCase(mapping.mappingFieldType) ?
                                            "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
                                    if (value instanceof Long) {
                                        value = new java.text.SimpleDateFormat(dateFormat).format(new Date((Long) value));
                                    } else if (value instanceof String) {
                                        try {
                                            Long timestamp = Long.parseLong((String) value);
                                            value = new java.text.SimpleDateFormat(dateFormat).format(new Date(timestamp));
                                        } catch (NumberFormatException e) {
                                            // 如果不是数字，保持原值
                                        }
                                    }
                                }
                                if (value != null && "Boolean".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                    System.out.println("Boolean5:"+value);
                                    if (value instanceof Number) {
                                        value = ((Number) value).intValue() == 1;
                                    } else if (value instanceof String) {
                                        String strValue = ((String) value).trim();
                                        value = "1".equals(strValue);
                                    }
                                }
                                // 处理SQL查询返回的数组值（如果字段配置为String类型）
                                if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && value instanceof JSONArray) {
                                    JSONArray array = (JSONArray) value;
                                    if (array.size() > 0) {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        for (Object o : array) {
                                            stringBuilder.append(o.toString());
                                            stringBuilder.append(";");
                                        }
                                        value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                    } else {
                                        value = "";
                                    }
                                }

                                // 处理值映射
                                if (valueMapping.containsKey(mapping.mappingField) && value != null) {
                                    Object mappedVal = valueMapping.get(mapping.mappingField).get(String.valueOf(value));
                                    if (mappedVal != null) {
                                        // 检查字段类型，如果是String类型但映射值是数组，则取第一个元素
                                        if ("String".equalsIgnoreCase(mapping.sfdcFieldType) && mappedVal instanceof JSONArray) {
                                            JSONArray array = (JSONArray) mappedVal;
                                            if (array.size() > 0) {
                                                StringBuilder stringBuilder = new StringBuilder();
                                                for (Object o : array) {
                                                    stringBuilder.append(o.toString());
                                                    stringBuilder.append(";");
                                                }
                                                value = stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                            } else {
                                                value = "";
                                            }
                                        } else {
                                            value = mappedVal;
                                        }
                                    }
                                }
                                nestedObj.put(mapping.mappingField, value);
                                hasValidFields = true;
                            }
                        }

                        // 只有当嵌套对象有有效字段时才添加
                        if (hasValidFields) {
                            result.put(mappingObject, nestedObj);
                        }
                    }
                }
            }

            return result;
        }

        // 其它类型直接返回
        return data;
    }

    // 辅助方法：在主记录中根据明细对象名寻找子查询返回的数组（key 形如：SELECT ... FROM <detailObj> ...）
    private static JSONArray findDetailArray(JSONObject record, String detailObjName) {
        if (record == null || detailObjName == null || detailObjName.trim().isEmpty()) {
            return null;
        }
        for (String key : record.keySet()) {
            String upperKey = key.toUpperCase(Locale.ROOT);
            String needle = (" FROM " + detailObjName).toUpperCase(Locale.ROOT);
            if (upperKey.contains("SELECT ") && upperKey.contains(needle)) {
                Object v = record.get(key);
                if (v instanceof JSONArray) {
                    return (JSONArray) v;
                }
            }
        }
        return null;
    }

    public static InterfaceConfig getInterfaceConfigFromSXY(String interfaceName) throws ApiEntityServiceException {
        String sql = "SELECT id, unique_API_Name__c, target_Object__c, http_Method__c,success_Flag__c, trancode__c, vSID__c, interface_Action_URL__c,full_URL__c, object_List__c FROM interface_Config__c WHERE unique_API_Name__c = '" + interfaceName + "'";
        QueryResult<JSONObject> query = XoqlService.instance().query(sql, true, false);
        if (query.getRecords().isEmpty()) {
            throw new ApiEntityServiceException("未找到接口配置: " + interfaceName);
        }
        JSONObject row = query.getRecords().get(0);
        InterfaceConfig config = new InterfaceConfig();
        config.id = row.getLong("id");
        config.uniqueApiName = row.getString("unique_API_Name__c");
        config.targetObject = row.getString("target_Object__c");
        config.httpMethod = row.getString("http_Method__c");
        config.tranCode = row.getString("trancode__c");
        config.vsid = row.getString("vSID__c");
        config.interfaceActionUrl = row.getString("full_URL__c");
        config.objectListType = row.getString("object_List__c");
//        config.apiToken = row.getString("api_Token__c");
        config.successFlag = row.getString("success_Flag__c");
        // 初始化HTTP头信息（可以根据需要从配置中获取）
        config.httpHeaders = new HashMap<>();
        config.httpHeaders.put("User-Agent", "Salesforce-Interface/1.0");

        // 设置默认超时时间
        config.httpTimeout = 30000; // 30秒

        return config;
    }

    // 严格修正字段映射获取SQL
    public static List<FieldMapping> getFieldMappingsFromSXY(String interfaceName, String direction,InterfaceConfig config) throws ApiEntityServiceException {
        List<FieldMapping> mappings = new ArrayList<>();
        if (config == null){
            config = getInterfaceConfigFromSXY(interfaceName);
        }
//        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);
        String sql = "SELECT mapping_Parent_Object__c, mapping_Object__c, mapping_Field__c, mapping_Field_Type__c, mapping_Field_Format__c, mapping_Value__c, sFDC_Parent_Object__c, sFDC_Object__c, sFDC_Field__c, sFDC_Field_Type__c, sFDC_Detail_Object__c, sFDC_Detail_Object_where__c FROM field_Mapping__c WHERE interface_Config__c ='" + config.id + "' ORDER BY sort_Order__c ASC";
        QueryResult<JSONObject> query = XoqlService.instance().query(sql, true, false);
        for (JSONObject row : query.getRecords()) {
            FieldMapping mapping = new FieldMapping();
            mapping.mappingParentObject = row.getString("mapping_Parent_Object__c");
            mapping.mappingObject = row.getString("mapping_Object__c");
            mapping.mappingField = row.getString("mapping_Field__c");
            Object mappingFieldTypeObj = row.get("mapping_Field_Type__c");
            String mappingFieldType = null;
            if (mappingFieldTypeObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) mappingFieldTypeObj;
                if (!arr.isEmpty()) {
                    mappingFieldType = arr.getString(0);
                }
            } else if (mappingFieldTypeObj instanceof String) {
                String str = (String) mappingFieldTypeObj;
                if (str.startsWith("[") && str.endsWith("]")) {
                    str = str.substring(1, str.length() - 1).replaceAll("\"", "");
                    String[] parts = str.split(",");
                    if (parts.length > 0) {
                        mappingFieldType = parts[0].trim();
                    }
                } else {
                    mappingFieldType = str;
                }
            }
            mapping.mappingFieldType = mappingFieldType;
            mapping.mappingFieldFormat = row.getString("mapping_Field_Format__c");
            mapping.mappingValue = row.getString("mapping_Value__c");
            mapping.sfdcParentObject = row.getString("sFDC_Parent_Object__c");
            mapping.sfdcObject = row.getString("sFDC_Object__c");
            mapping.sfdcField = row.getString("sFDC_Field__c");
            mapping.sfdcDetailObject = row.getString("sFDC_Detail_Object__c");
            mapping.sfdcDetailObjectWhere = row.getString("sFDC_Detail_Object_where__c");
            Object fieldTypeObj = row.get("sFDC_Field_Type__c");
            String fieldType = null;
            if (fieldTypeObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) fieldTypeObj;
                if (!arr.isEmpty()) {
                    fieldType = arr.getString(0);
                }
            } else if (fieldTypeObj instanceof String) {
                String str = (String) fieldTypeObj;
                if (str.startsWith("[") && str.endsWith("]")) {
                    str = str.substring(1, str.length() - 1).replaceAll("\"", "");
                    String[] parts = str.split(",");
                    if (parts.length > 0) {
                        fieldType = parts[0].trim();
                    }
                } else {
                    fieldType = str;
                }
            }
            mapping.sfdcFieldType = fieldType;
            mappings.add(mapping);
        }
        System.out.println(JSONObject.toJSONString(mappings));
        return mappings;
    }

    /**
     * 总入口：根据接口名和方向自动完成数据映射
     * @param interfaceName 接口名称
     * @param direction 方向（inbound/outbound）
     * @param jsonData 外部数据
     * @return 系统字段Map
     */
    public static JSONObject autoMapping(String interfaceName, String direction, JSONObject jsonData) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, direction,null);
        // 获取字段值映射配置
        Map<String, Map<String, Object>> valueMapping = getFieldValueMappingByMappingValue(interfaceName);
        System.out.println("字段值映射配置"+JSON.toJSONString(valueMapping));
        Map<String, List<Object>> result;

        // 入站批量支持：当 jsondata 为数组时，逐条处理并合并结果
        Object jsondataNode = jsonData.get("jsondata");
        if (jsondataNode instanceof JSONArray && ((JSONArray) jsondataNode).size() > 1) {
            Map<String, List<Object>> combined = new HashMap<>();
            JSONArray arr = (JSONArray) jsondataNode;
            for (int i = 0; i < arr.size(); i++) {
                Object one = arr.get(i);
                // 单条直接作为数据根（包含 ORDER__C / ORDERITEM__C / ORDERPLANITEM__C 等）
                JSONObject dataRoot;
                if (one instanceof JSONObject) {
                    dataRoot = (JSONObject) one;
                } else {
                    // 防御：非对象时跳过
                    continue;
                }

                Map<String, List<Object>> singleResult = mapToSfdcFields(dataRoot, mappings);
                // 值映射
                applyValueMapping(singleResult, valueMapping, mappings);
                // 合并到 combined
                for (Map.Entry<String, List<Object>> e : singleResult.entrySet()) {
                    combined.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
                }
            }
            result = combined;
        } else {
            // 非批量：若包含 jsondata 且为对象，则以其为根；否则直接用原 jsonData
            if (jsondataNode instanceof JSONObject) {
                result = mapToSfdcFields((JSONObject) jsondataNode, mappings);
            } else {
                result = mapToSfdcFields(jsonData, mappings);
            }
            // 应用字段值映射
            applyValueMapping(result, valueMapping, mappings);
        }

        // 将Map<String, List<Object>>转换为Map<String, Object>以便JSONObject可以处理
        Map<String, Object> jsonResult = new HashMap<>();
        for (Map.Entry<String, List<Object>> entry : result.entrySet()) {
            String key = entry.getKey();
            List<Object> value = entry.getValue();
            jsonResult.put(key, value);
        }

        // head字段保持为单个对象，不转换为数组
        if (jsonData.containsKey("head")){
            jsonResult.put("head", jsonData.get("head"));
        }

        return new JSONObject(jsonResult);
    }
    /**
     * 应用字段值映射到结果数据
     * @param result 映射结果数据
     * @param valueMapping 字段值映射配置
     * @param mappings 字段映射配置
     */
    private static void applyValueMapping(Map<String, List<Object>> result, Map<String, Map<String, Object>> valueMapping, List<FieldMapping> mappings) {
        for (Map.Entry<String, List<Object>> objectEntry : result.entrySet()) {
            String objectName = objectEntry.getKey();
            List<Object> objectDataList = objectEntry.getValue();

            // 处理List中的每个对象
            for (Object objectData : objectDataList) {
                if (objectData instanceof Map) {
                    Map<String, Object> fieldMap = (Map<String, Object>) objectData;

                    for (FieldMapping mapping : mappings) {
                        String sfdcObject = mapping.sfdcObject != null ? mapping.sfdcObject : "default";

                        // 只处理当前对象的字段
                        if (objectName.equals(sfdcObject) && fieldMap.containsKey(mapping.sfdcField)) {
                            Object currentValue = fieldMap.get(mapping.sfdcField);

                            // Multiplechoice 多选字段特殊处理
                            if (currentValue != null && "Multiplechoice".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                String raw = String.valueOf(currentValue);
                                // 分隔符支持中英文逗号与分号
                                String[] parts = raw.split("[;,，、]+");
                                List<Object> mappedParts = new ArrayList<>();
                                Map<String, Object> fieldValueMap = valueMapping.get(mapping.mappingField);
                                for (String p : parts) {
                                    String token = p == null ? "" : p.trim();
                                    if (token.isEmpty()) continue;
                                    if (fieldValueMap != null) {
                                        Object mv = fieldValueMap.get(token);
                                        mappedParts.add(mv == null ? token : mv);
                                    } else {
                                        mappedParts.add(token);
                                    }
                                }
                                JSONArray arrayVal = new JSONArray();
                                for (Object v : mappedParts) { arrayVal.add(v); }
                                fieldMap.put(mapping.sfdcField, arrayVal);
                                continue; // 已处理，进入下一个字段
                            }

                            // 其他类型：按原有单值映射逻辑处理
                            // 检查是否存在值映射配置
                            if (valueMapping.containsKey(mapping.mappingField) && currentValue != null) {
                                String currentValueStr = String.valueOf(currentValue);
                                Object mappedValue = valueMapping.get(mapping.mappingField).get(currentValueStr);

                                if (mappedValue != null) {
                                    fieldMap.put(mapping.sfdcField, mappedValue);
                                    System.out.println("字段值映射应用: " + mapping.mappingField + " [" + currentValueStr + "] -> [" + mappedValue + "]");
                                } else if ("Multiplechoice".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                    // 如果配置存在但未匹配到具体项，同时类型是多选，则拆分为数组
                                    String[] parts = currentValueStr.split("[;,，、]+");
                                    JSONArray arrayVal = new JSONArray();
                                    for (String p : parts) { String t = p == null ? "" : p.trim(); if (!t.isEmpty()) arrayVal.add(t); }
                                    fieldMap.put(mapping.sfdcField, arrayVal);
                                }
                            } else if (currentValue != null && "Multiplechoice".equalsIgnoreCase(mapping.sfdcFieldType)) {
                                // 没有值映射配置，拆分为数组
                                String[] parts = String.valueOf(currentValue).split("[;,，、]+");
                                JSONArray arrayVal = new JSONArray();
                                for (String p : parts) { String t = p == null ? "" : p.trim(); if (!t.isEmpty()) arrayVal.add(t); }
                                fieldMap.put(mapping.sfdcField, arrayVal);
                            }
                        }

                    // 子表/孙表：对嵌套的 List<Map> 进行字段值映射（如 ORDERITEM__C 下的 WAERS -> currencyUnit）
                    for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
                        String childListName = entry.getKey();
                        Object childVal = entry.getValue();
                        if (!(childVal instanceof List)) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        List<Object> childRows = (List<Object>) childVal;

                        for (FieldMapping childMapping : mappings) {
                            // 匹配子表对象（支持 sfdcObject 或 sfdcDetailObject）
                            boolean matchChild = isSfdcObjectMatch(childListName, childMapping);
                            if (!matchChild) continue;
                            if (childMapping.sfdcField == null || childMapping.sfdcField.isEmpty()) continue;

                            Map<String, Object> fieldValueMap = valueMapping.get(childMapping.mappingField);

                            for (Object rowObj : childRows) {
                                if (!(rowObj instanceof Map)) continue;
                                @SuppressWarnings("unchecked")
                                Map<String, Object> row = (Map<String, Object>) rowObj;
                                if (!row.containsKey(childMapping.sfdcField)) continue;

                                Object currentValue = row.get(childMapping.sfdcField);
                                if (currentValue == null) continue;

                                // 多选
                                if ("Multiplechoice".equalsIgnoreCase(childMapping.sfdcFieldType)) {
                                    String raw = String.valueOf(currentValue);
                                    String[] parts = raw.split("[;,，、]+");
                                    List<Object> mappedParts = new ArrayList<>();
                                    if (fieldValueMap != null) {
                                        for (String p : parts) {
                                            String token = p == null ? "" : p.trim();
                                            if (token.isEmpty()) continue;
                                            Object mv = fieldValueMap.get(token);
                                            if (mv == null) mv = fieldValueMap.get(token.toUpperCase());
                                            if (mv == null) mv = fieldValueMap.get(token.toLowerCase());
                                            mappedParts.add(mv == null ? token : mv);
                                        }
                                    } else {
                                        for (String p : parts) {
                                            String token = p == null ? "" : p.trim();
                                            if (!token.isEmpty()) mappedParts.add(token);
                                        }
                                    }
                                    JSONArray arrayVal = new JSONArray();
                                    for (Object v : mappedParts) { arrayVal.add(v); }
                                    row.put(childMapping.sfdcField, arrayVal);
                                    continue;
                                }

                                // 单值
                                if (fieldValueMap != null) {
                                    String rawKey = String.valueOf(currentValue);
                                    Object mappedValue = fieldValueMap.get(rawKey);
                                    if (mappedValue == null) mappedValue = fieldValueMap.get(rawKey.trim());
                                    if (mappedValue == null) mappedValue = fieldValueMap.get(rawKey.trim().toUpperCase());
                                    if (mappedValue == null) mappedValue = fieldValueMap.get(rawKey.trim().toLowerCase());
                                    if (mappedValue != null) {
                                        row.put(childMapping.sfdcField, mappedValue);
                                        System.out.println("字段值映射应用(子表): " + childMapping.mappingField + " [" + rawKey + "] -> [" + mappedValue + "]");
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    /**
     * 总入口：根据接口名和方向自动完成反向数据映射（系统字段Map -> 外部结构）
     * @param interfaceName 接口名称
     * @param direction 方向（inbound/outbound）
     * @param sfdcData 系统字段Map
     * @return 外部字段结构（JSONObject）
     */
    public static JSONObject autoReverseMapping(String interfaceName, String direction, Map<String, Object> sfdcData) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, direction,null);
        Map<String, Map<String, Object>> valueMapping = getFieldValueMappingBySfdcValue(interfaceName);

        // 将Map转换为JSONObject以便使用mapToExternalFieldsRecursive
        JSONObject jsonData = new JSONObject();
        for (Map.Entry<String, Object> entry : sfdcData.entrySet()) {
            jsonData.put(entry.getKey(), entry.getValue());
        }

        Object result = mapToExternalFieldsRecursive(jsonData, mappings, valueMapping);
        if (result instanceof JSONObject) {
            return (JSONObject) result;
        } else {
            // 如果结果不是JSONObject，创建一个包含结果的JSONObject
            JSONObject wrapper = new JSONObject();
            wrapper.put("result", result);
            return wrapper;
        }
    }

    /**
     * 根据接口名和id列表自动拼接SQL并查询目标对象数据（修正主子表SQL拼接）
     * @param interfaceName 接口唯一名
     * @param idList 需要查询的id列表
     * @param direction 方向（inbound/outbound）
     * @return 查询结果List<JSONObject>
     */
    public static List<JSONObject> queryTargetObjects(String interfaceName, List<String> idList, String direction,InterfaceConfig config) throws ApiEntityServiceException {
        if (config == null){
            config = getInterfaceConfigFromSXY(interfaceName);
        }
//        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, direction,config);

        // 主子表分组SQL拼接
        Set<String> mainFieldsSet = new LinkedHashSet<>(); // 使用Set避免重复字段
        Map<String, Set<String>> subFieldsMap = new LinkedHashMap<>(); // 使用Set避免重复字段
        Map<String, String> subWhereMap = new LinkedHashMap<>();

        for (FieldMapping mapping : mappings) {
            // 检查字段名是否为空，避免SQL中出现空字段
            if (mapping.sfdcField == null || mapping.sfdcField.trim().isEmpty()) {
                continue; // 跳过空字段
            }

            if (mapping.sfdcDetailObject == null || mapping.sfdcDetailObject.isEmpty()) {
                mainFieldsSet.add(mapping.sfdcField); // 使用Set自动去重
            } else {
                subFieldsMap.computeIfAbsent(mapping.sfdcDetailObject, k -> new LinkedHashSet<>()).add(mapping.sfdcField);
                // 保存WHERE条件（每个明细对象只保存一次）
                if (!subWhereMap.containsKey(mapping.sfdcDetailObject)) {
                    subWhereMap.put(mapping.sfdcDetailObject, mapping.sfdcDetailObjectWhere);
                }
            }
        }

        StringBuilder subSelects = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : subFieldsMap.entrySet()) {
            String detailObj = entry.getKey();
            Set<String> fields = entry.getValue();

            // 过滤掉空字段
            List<String> validFields = new ArrayList<>();
            for (String field : fields) {
                if (field != null && !field.trim().isEmpty()) {
                    validFields.add(field);
                }
            }

            // 如果没有有效字段，跳过这个子查询
            if (validFields.isEmpty()) {
                continue;
            }

            String subFields = String.join(", ", validFields);
            String whereCondition = subWhereMap.get(detailObj);

            if (whereCondition != null && !whereCondition.trim().isEmpty()) {
                subSelects.append(", (SELECT ").append(subFields).append(" FROM ").append(detailObj).append(" WHERE ").append(whereCondition).append(")");
            } else {
                subSelects.append(", (SELECT ").append(subFields).append(" FROM ").append(detailObj).append(")");
            }
        }

        String idListStr = "'" + String.join("','", idList) + "'";

        // 构建完整的SELECT语句
        StringBuilder sqlBuilder = new StringBuilder("SELECT ");

        // 添加主表字段（如果有的话）
        if (!mainFieldsSet.isEmpty()) {
            String mainFields = String.join(", ", mainFieldsSet);
            sqlBuilder.append(mainFields);
        }

        // 添加子查询
        sqlBuilder.append(subSelects);

        // 如果主表字段为空，需要添加一个占位符避免语法错误
        if (mainFieldsSet.isEmpty() && subSelects.length() == 0) {
            sqlBuilder.append("id"); // 至少查询id字段
        }

        sqlBuilder.append(" FROM ").append(config.targetObject).append(" WHERE id IN (").append(idListStr).append(")");

        String sql = sqlBuilder.toString();
        System.out.println("Sql拼接打印：" + sql);
        QueryResult<JSONObject> query = XoqlService.instance().query(sql, true, false);
        System.out.println("查询返回数据："+query.getRecords());
        return query.getRecords();
    }

    /***
     * @Description: sql拼接
     * @Param: [interfaceName, idList, direction, config]
     * @return: java.lang.String
     * @Author: 武于伦
     * @email: 2717718875@qq.com
     * @Date: 2025/9/9
     */
    public static String querySql(String interfaceName, List<String> idList, String direction,InterfaceConfig config) throws ApiEntityServiceException {
        if (config == null){
            config = getInterfaceConfigFromSXY(interfaceName);
        }
//        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, direction,config);

        // 主子表分组SQL拼接
        Set<String> mainFieldsSet = new LinkedHashSet<>(); // 使用Set避免重复字段
        Map<String, Set<String>> subFieldsMap = new LinkedHashMap<>(); // 使用Set避免重复字段
        Map<String, String> subWhereMap = new LinkedHashMap<>();

        for (FieldMapping mapping : mappings) {
            // 检查字段名是否为空，避免SQL中出现空字段
            if (mapping.sfdcField == null || mapping.sfdcField.trim().isEmpty()) {
                continue; // 跳过空字段
            }

            if (mapping.sfdcDetailObject == null || mapping.sfdcDetailObject.isEmpty()) {
                mainFieldsSet.add(mapping.sfdcField); // 使用Set自动去重
            } else {
                subFieldsMap.computeIfAbsent(mapping.sfdcDetailObject, k -> new LinkedHashSet<>()).add(mapping.sfdcField);
                // 保存WHERE条件（每个明细对象只保存一次）
                if (!subWhereMap.containsKey(mapping.sfdcDetailObject)) {
                    subWhereMap.put(mapping.sfdcDetailObject, mapping.sfdcDetailObjectWhere);
                }
            }
        }

        StringBuilder subSelects = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : subFieldsMap.entrySet()) {
            String detailObj = entry.getKey();
            Set<String> fields = entry.getValue();

            // 过滤掉空字段
            List<String> validFields = new ArrayList<>();
            for (String field : fields) {
                if (field != null && !field.trim().isEmpty()) {
                    validFields.add(field);
                }
            }

            // 如果没有有效字段，跳过这个子查询
            if (validFields.isEmpty()) {
                continue;
            }

            String subFields = String.join(", ", validFields);
            String whereCondition = subWhereMap.get(detailObj);

            if (whereCondition != null && !whereCondition.trim().isEmpty()) {
                subSelects.append(", (SELECT ").append(subFields).append(" FROM ").append(detailObj).append(" WHERE ").append(whereCondition).append(")");
            } else {
                subSelects.append(", (SELECT ").append(subFields).append(" FROM ").append(detailObj).append(")");
            }
        }

        String idListStr = "'" + String.join("','", idList) + "'";

        // 构建完整的SELECT语句
        StringBuilder sqlBuilder = new StringBuilder("SELECT ");

        // 添加主表字段（如果有的话）
        if (!mainFieldsSet.isEmpty()) {
            String mainFields = String.join(", ", mainFieldsSet);
            sqlBuilder.append(mainFields);
        }

        // 添加子查询
        sqlBuilder.append(subSelects);

        // 如果主表字段为空，需要添加一个占位符避免语法错误
        if (mainFieldsSet.isEmpty() && subSelects.length() == 0) {
            sqlBuilder.append("id"); // 至少查询id字段
        }

        sqlBuilder.append(" FROM ").append(config.targetObject).append(" WHERE id IN (").append(idListStr).append(")");

        String sql = sqlBuilder.toString();
        System.out.println("Sql拼接打印：" + sql);
        return sql;
    }

    public static List<JSONObject> getFieldValueMappingsFromSXY(String interfaceName) throws ApiEntityServiceException {
        String sql = "SELECT fM_Mapping_Field__c, sFDC_Value__c, mapping_Value__c FROM field_Value_Mapping__c WHERE interface_Config_Unique_API_Name__c = '" + interfaceName + "' AND interface_Config_Active__c = 1";
        QueryResult<JSONObject> query = XoqlService.instance().query(sql, true, false);
        return query.getRecords();
    }

    /**
     * 按SFDC值为key获取字段值映射Map
     * @param interfaceName 接口唯一名
     * @return Map<字段名, Map<SFDC值, 映射值>>
     */
    public static Map<String, Map<String, Object>> getFieldValueMappingBySfdcValue(String interfaceName) throws ApiEntityServiceException {
        List<JSONObject> valueMappings = getFieldValueMappingsFromSXY(interfaceName);
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (JSONObject row : valueMappings) {
            String field = row.getString("fM_Mapping_Field__c");
            String sfdcValue = row.getString("sFDC_Value__c");
            Object mappingValue = row.get("mapping_Value__c");
            result.computeIfAbsent(field, k -> new HashMap<>()).put(sfdcValue, mappingValue);
        }
        return result;
    }

    /**
     * 按映射值为key获取字段值映射Map
     * @param interfaceName 接口唯一名
     * @return Map<字段名, Map<映射值, SFDC值>>
     */
    public static Map<String, Map<String, Object>> getFieldValueMappingByMappingValue(String interfaceName) throws ApiEntityServiceException {
        List<JSONObject> valueMappings = getFieldValueMappingsFromSXY(interfaceName);
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (JSONObject row : valueMappings) {
            String field = row.getString("fM_Mapping_Field__c");
            Object mappingValue = row.get("mapping_Value__c");
            String sfdcValue = row.getString("sFDC_Value__c");
            result.computeIfAbsent(field, k -> new HashMap<>()).put(String.valueOf(mappingValue), sfdcValue);
        }
        return result;
    }

    /**
     * 按SFDC对象分组的字段映射（主表/子表结构）
     * @param interfaceName 接口唯一名
     * @return Map<SFDC对象名, Map<SFDC字段名, 字段映射配置>>
     */
    public static Map<String, Map<String, FieldMapping>> getSfdcObjInterfaceMappingsFromSXY(String interfaceName) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, "outbound",null);
        Map<String, Map<String, FieldMapping>> result = new HashMap<>();
        for (FieldMapping mapping : mappings) {
            String sfdcObj = mapping.sfdcObject != null ? mapping.sfdcObject : "default";
            result.computeIfAbsent(sfdcObj, k -> new HashMap<>()).put(mapping.sfdcField, mapping);
        }
        return result;
    }

    /**
     * 按外部对象分组的字段映射
     * @param interfaceName 接口唯一名
     * @return Map<外部对象名, Map<外部字段名, 字段映射配置>>
     */
    public static Map<String, Map<String, FieldMapping>> getMappingObjInterfaceMappingsFromSXY(String interfaceName) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, "outbound",null);
        Map<String, Map<String, FieldMapping>> result = new HashMap<>();
        for (FieldMapping mapping : mappings) {
            String mappingObj = mapping.mappingObject != null ? mapping.mappingObject : "default";
            result.computeIfAbsent(mappingObj, k -> new HashMap<>()).put(mapping.mappingField, mapping);
        }
        return result;
    }

    /**
     * SFDC明细对象到外部对象的映射关系
     * @param interfaceName 接口唯一名
     * @return Map<SFDC明细对象名, 外部对象名>
     */
    public static Map<String, String> getDetailObjToMappingFromSXY(String interfaceName) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, "outbound",null);
        Map<String, String> result = new HashMap<>();
        for (FieldMapping mapping : mappings) {
            if (mapping.sfdcParentObject != null && mapping.mappingObject != null) {
                result.put(mapping.sfdcParentObject, mapping.mappingObject);
            }
        }
        return result;
    }

    /**
     * 外部对象到SFDC明细对象的映射关系
     * @param interfaceName 接口唯一名
     * @return Map<外部对象名, SFDC明细对象名>
     */
    public static Map<String, String> getObjMappingToDetailFromSXY(String interfaceName) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, "outbound",null);
        Map<String, String> result = new HashMap<>();
        for (FieldMapping mapping : mappings) {
            if (mapping.sfdcParentObject != null && mapping.mappingObject != null) {
                result.put(mapping.mappingObject, mapping.sfdcParentObject);
            }
        }
        return result;
    }

    /**
     * 获取List/Object类型字段的映射
     * @param interfaceName 接口唯一名
     * @return Map<字段名, 字段映射配置>
     */
    public static Map<String, FieldMapping> getObjectListTypeFieldMappingFromSXY(String interfaceName) throws ApiEntityServiceException {
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, "outbound",null);
        Map<String, FieldMapping> result = new HashMap<>();
        for (FieldMapping mapping : mappings) {
            if ("List".equalsIgnoreCase(mapping.sfdcFieldType) || "Object".equalsIgnoreCase(mapping.sfdcFieldType)) {
                result.put(mapping.sfdcField, mapping);
            }
        }
        return result;
    }

    /**
     * 根据字段映射和id列表自动组装推送用的JSON字符串
     * @param interfaceName 接口唯一名
     * @param idList 需要推送的id列表
     * @param direction 方向（inbound/outbound）
     * @return JSON字符串
     */
    public static String generateSendDataJsonString(String interfaceName, List<String> idList, String direction,InterfaceConfig config) throws ApiEntityServiceException {
        // 获取接口配置
        if (config == null) {
            config = getInterfaceConfigFromSXY(interfaceName);
        }
//        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);

        // 查询目标对象数据
        List<JSONObject> records = queryTargetObjects(interfaceName, idList, direction,config);
        // 获取字段映射和（可选）值映射
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, direction,config);
        Map<String, Map<String, Object>> valueMapping = getFieldValueMappingBySfdcValue(interfaceName);

        // 组装结果
        JSONArray resultArr = new JSONArray();

        // 如果没有查询到数据，返回包含空字段的默认结构
        if (records.isEmpty()) {
            System.out.println("警告: 没有查询到数据，返回默认空结构");
            JSONObject defaultRecord = new JSONObject();

            // 按映射对象分组处理
            Map<String, List<FieldMapping>> objectMappings = new HashMap<>();
            for (FieldMapping mapping : mappings) {
                String mappingObject = mapping.mappingObject != null ? mapping.mappingObject : "";
                objectMappings.computeIfAbsent(mappingObject, k -> new ArrayList<>()).add(mapping);
            }

            // 处理每个映射对象
            for (Map.Entry<String, List<FieldMapping>> entry : objectMappings.entrySet()) {
                String mappingObject = entry.getKey();
                List<FieldMapping> objectMappingList = entry.getValue();

                if (mappingObject.isEmpty()) {
                    // 直接字段映射（不嵌套）
                    for (FieldMapping mapping : objectMappingList) {
                        if (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                            defaultRecord.put(mapping.mappingField, "");
                        }
                    }
                } else {
                    // 嵌套对象映射
                    JSONObject nestedObj = new JSONObject();
                    boolean hasValidFields = false;

                    for (FieldMapping mapping : objectMappingList) {
                        if (mapping.mappingField != null && !mapping.mappingField.trim().isEmpty()) {
                            nestedObj.put(mapping.mappingField, "");
                            hasValidFields = true;
                        }
                    }

                    // 只有当嵌套对象有有效字段时才添加
                    if (hasValidFields) {
                        defaultRecord.put(mappingObject, nestedObj);
                    }
                }
            }

            resultArr.add(defaultRecord);
        } else {
            // 正常处理查询到的数据
            for (JSONObject record : records) {
                Object mapped = mapToExternalFieldsRecursive(record, mappings, valueMapping);
                resultArr.add(mapped);
            }

            // 特殊出站拆分处理：根据接口名对 COMPANY 与 SALES 进行多条拆分（带值映射）
            applyOutboundSplitting(interfaceName, resultArr, valueMapping);
        }

        // 根据objectListType配置决定JSON格式
        String objectListType = config.objectListType;
        if (objectListType == null || objectListType.trim().isEmpty()) {
            objectListType = "Object"; // 默认为Object类型（与Apex版本一致）
        }

        System.out.println("*** objectListType配置值: " + objectListType);

        if ("List".equalsIgnoreCase(objectListType)) {
            // 返回数组格式: [[{...}]]
            System.out.println("*** 使用List格式: " + resultArr.toJSONString());
            return resultArr.toJSONString();
        } else if ("Object".equalsIgnoreCase(objectListType)) {
            // 返回单个对象格式: {...}（不带外层数组）
            if (resultArr.size() > 0) {
                String result = resultArr.get(0).toString();
                System.out.println("*** 使用Object格式: " + result);
                return result;
            } else {
                System.out.println("*** 使用Object格式（空）: {}");
                return "{}";
            }
        } else {
            // 其他情况默认返回数组格式: [[{...}]]
            System.out.println("*** 使用默认数组格式: " + resultArr.toJSONString());
            return resultArr.toJSONString();
        }
    }

    /**
     * 根据字段映射和id列表自动组装推送用的XML字符串（动态格式选择）
     * @param interfaceName 接口唯一名
     * @param idList 需要推送的id列表
     * @param direction 方向（inbound/outbound）
     * @return XML字符串
     */
    public static String generateSendDataXmlString(String interfaceName, List<String> idList, String direction,InterfaceConfig config) throws ApiEntityServiceException {
        // 1. 获取接口配置
        if (config == null) {
            config = getInterfaceConfigFromSXY(interfaceName);
        }
//        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);

        // 2. 根据接口配置决定XML格式
        // 参考Utilities_Interface.cls的逻辑，根据vsid值选择格式：
        // - vsid=000: vportal本地服务
        // - vsid=001: 访问sap服务 (使用简单格式)
        // - vsid=002: 访问用户信息服务（人力系统）
        // - vsid=003: 智能制造管理平台 (使用复杂格式)
        // - vsid=004: OA系统
        // - vsid=005: 短信平台
        // - vsid=006: 供应链系统（预留）
        // - vsid=007: PLM系统
        if (config.vsid != null) {
            switch (config.vsid) {
                case "016":
                case "060":
                    // SAP服务使用简单格式（jsondata）
                    return generateSimpleXmlString(interfaceName, idList, direction, config);
                case "001":
                case "002":
                case "003":
                case "004":
                case "005":
                case "006":
                case "007":
                    // 其他服务使用复杂格式（fields+tables）
                    return generateComplexXmlString(interfaceName, idList, direction, config);
                default:
                    // 未知vsid值，使用默认复杂格式
                    System.out.println("警告: 未知的vsid值: " + config.vsid + "，使用默认复杂格式");
                    return generateComplexXmlString(interfaceName, idList, direction, config);
            }
        } else {
            // vsid为空，使用默认复杂格式
            System.out.println("警告: vsid值为空，使用默认复杂格式");
            return generateComplexXmlString(interfaceName, idList, direction, config);
        }
    }

    /**
     * 对应Utilities_Interface.outboundSAPVportalExecuteNoFuture方法
     * 生成简单格式XML（jsondata格式）
     * @param interfaceName 接口唯一名
     * @param idList 需要推送的id列表
     * @param direction 方向（inbound/outbound）
     * @return XML字符串
     */
    public static String outboundSAPVportalExecuteNoFuture(String interfaceName, List<String> idList, String direction) throws ApiEntityServiceException {
        // 1. 获取接口配置
        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);

        // 2. 生成JSON数据（对应generateVPSendDataString）
        String jsonData = generateSendDataJsonString(interfaceName, idList, direction,config);

        // 3. 根据接口名进行特殊处理（参考Utilities_Interface.cls的appendxml方法）
        if ("AccountToSAP".equals(interfaceName) || "FinAccountToSAP".equals(interfaceName) ||
                "AccountToSAPUpdate".equals(interfaceName) || "SupplierToSAP".equals(interfaceName)) {
            jsonData = jsonData.replaceAll("\"null\"", "\"\"");
            jsonData = jsonData.replaceAll("null", "\"\"");
        }

        // 4. 构建简单格式XML（对应appendxml方法）
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?><request><head>");
        xml.append("<trancode>").append(config.tranCode).append("</trancode>");
        xml.append("<code>0</code>");
        xml.append("<reqserno>").append(config.tranCode).append(String.valueOf(System.currentTimeMillis())).append("</reqserno>");
        xml.append("<asynreq>0</asynreq><asyncode>0</asyncode>");
        xml.append("<vsid>").append(config.vsid).append("</vsid>");
        xml.append("</head><fields><jsondata><![CDATA[").append(jsonData).append("]]></jsondata></fields><tables></tables></request>");

        return xml.toString();
    }

    /**
     * 对应Utilities_Interface.outboundVporalExecuteNoFuture方法
     * 生成复杂格式XML（fields+tables格式）
     * @param interfaceName 接口唯一名
     * @param idList 需要推送的id列表
     * @param direction 方向（inbound/outbound）
     * @return XML字符串
     */
    public static String outboundVporalExecuteNoFuture(String interfaceName, List<String> idList, String direction) throws ApiEntityServiceException {
        // 1. 获取接口配置
        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);

        // 2. 生成复杂格式XML（对应generateDataList方法）
        return generateComplexXmlString(interfaceName, idList, direction, config);
    }

    /**
     * 生成简单格式XML（jsondata格式）
     */
    private static String generateSimpleXmlString(String interfaceName, List<String> idList, String direction, InterfaceConfig config) throws ApiEntityServiceException {
        // 1. 生成JSON数据
        String jsonData = generateSendDataJsonString(interfaceName, idList, direction,config);

        // 2. 根据接口名进行特殊处理（参考Utilities_Interface.cls的appendxml方法）
        if ("FICOINORDER".equals(interfaceName)) {
            jsonData = jsonData.replaceAll("\r\n", "");
            jsonData = jsonData.replaceAll("\n", "");
            jsonData = jsonData.replaceAll("\r", "");
            jsonData = jsonData.replaceAll("null", "\"\"");
        }
        if ("FICOWorkTime".equals(interfaceName)) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
            String dd = sdf.format(new Date());
            System.out.println("*** day: " + dd);

            jsonData = "\"IT_ITEM\":" + jsonData;
            jsonData = "[{\"DOCDATE\":\"" + dd + "\"," + jsonData + "}]";
            jsonData = jsonData.replaceAll("\r\n", "");
            jsonData = jsonData.replaceAll("\n", "");
            jsonData = jsonData.replaceAll("\r", "");
            jsonData = jsonData.replaceAll("null", "\"\"");
        }
        if ("CaseToSap".equals(interfaceName) || "InventoryToSap".equals(interfaceName)) {
            jsonData = jsonData.replaceAll("\r\n", "");
            jsonData = jsonData.replaceAll("\n", "");
            jsonData = jsonData.replaceAll("\r", "");
            jsonData = jsonData.replaceAll("null", "\"\"");
        }
        if ("AccountToSAP".equals(interfaceName) || "FinAccountToSAP".equals(interfaceName) ||
                "AccountToSAPUpdate".equals(interfaceName) || "SupplierToSAP".equals(interfaceName)) {
            jsonData = jsonData.replaceAll("\"null\"", "\"\"");
            jsonData = jsonData.replaceAll("null", "\"\"");
        }
        if ("CaseToBPC".equals(interfaceName) ||
                "ProjectToBPC".equals(interfaceName) ||
                "BidToBPC".equals(interfaceName)) {
            jsonData = jsonData.replaceAll("\"null\"", "\"\"");
            jsonData = jsonData.replaceAll("null", "\"\"");
        }
        // 3. 构建简单格式XML
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?><request><head>");
        xml.append("<trancode>").append(config.tranCode).append("</trancode>");
        xml.append("<code>0</code>");
        xml.append("<reqserno>").append(config.tranCode).append(String.valueOf(System.currentTimeMillis())).append("</reqserno>");
        xml.append("<asynreq>0</asynreq><asyncode>0</asyncode>");
        xml.append("<vsid>").append(config.vsid).append("</vsid>");
        xml.append("</head><fields><jsondata><![CDATA[").append(jsonData).append("]]></jsondata></fields><tables></tables></request>");

        return xml.toString();
    }

    /**
     * 生成复杂格式XML（fields+tables格式）
     */
    private static String generateComplexXmlString(String interfaceName, List<String> idList, String direction, InterfaceConfig config) throws ApiEntityServiceException {
        // 1. 先生成映射后的JSON数据
        String jsonData = generateSendDataJsonString(interfaceName, idList, direction,config);
        jsonData = ensureJsonArrayFormat(jsonData);
        // 2. 解析映射后的JSON数据
        JSONArray mappedRecords = JSONArray.parseArray(jsonData);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?><request><head>");
        xml.append("<trancode>").append(config.tranCode).append("</trancode>");
        xml.append("<reqserno>").append(config.tranCode).append(interfaceName).append(System.currentTimeMillis()).append("</reqserno>");
        xml.append("<code>0</code><asynreq>0</asynreq><asyncode>0</asyncode>");
        xml.append("<vsid>").append(config.vsid).append("</vsid>");
        xml.append("<EXEC>CALL-EXEC</EXEC></head>");

        // 3. 处理所有记录
        for (int recordIndex = 0; recordIndex < mappedRecords.size(); recordIndex++) {
            JSONObject mappedRecord = mappedRecords.getJSONObject(recordIndex);

            // 主表字段（动态获取主对象名）
            xml.append("<fields>");
            for (String objectName : mappedRecord.keySet()) {
                Object objectData = mappedRecord.get(objectName);
                if (objectData instanceof JSONObject) {
                    JSONObject mainData = (JSONObject) objectData;
                    for (String fieldName : mainData.keySet()) {
                        Object value = mainData.get(fieldName);
                        xml.append("<").append(fieldName).append("><![CDATA[").append(value == null ? "" : value).append("]]></").append(fieldName).append(">");
                    }
                }
            }
            xml.append("</fields>");

            // 4. 子表数据（动态处理所有子对象）
            xml.append("<tables>");
            for (String objectName : mappedRecord.keySet()) {
                Object objectData = mappedRecord.get(objectName);
                if (objectData instanceof JSONArray) {
                    JSONArray detailData = (JSONArray) objectData;

                    if (detailData != null && !detailData.isEmpty()) {
                        xml.append("<table tablename=\"").append(objectName).append("\">");
                        xml.append("<thead>");

                        // 获取列名（从第一条记录中获取）
                        JSONObject firstItem = detailData.getJSONObject(0);
                        for (String colName : firstItem.keySet()) {
                            xml.append("<col>").append(colName).append("</col>");
                        }
                        xml.append("</thead><tbody>");

                        // 生成数据行
                        for (int i = 0; i < detailData.size(); i++) {
                            JSONObject item = detailData.getJSONObject(i);
                            xml.append("<row>");
                            for (String colName : firstItem.keySet()) {
                                Object value = item.get(colName);
                                xml.append("<c><![CDATA[").append(value == null ? "" : value).append("]]></c>");
                            }
                            xml.append("</row>");
                        }
                        xml.append("</tbody></table>");
                    }
                }
            }
            xml.append("</tables>");
        }

        xml.append("</request>");
        return xml.toString();
    }
    /**
     * 确保JSON字符串具有正确的数组格式
     * @param jsonData JSON字符串
     * @return 规范化后的JSON字符串（确保有[]包围）
     */
    private static String ensureJsonArrayFormat(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return "[]";
        }

        String trimmed = jsonData.trim();

        // 如果已经是数组格式，直接返回
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            System.out.println("*** JSON已为数组格式: " + trimmed);
            return trimmed;
        }

        // 如果是单个对象，包装成数组
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String result = "[" + trimmed + "]";
            System.out.println("*** JSON对象包装为数组: " + result);
            return result;
        }

        // 其他情况，直接包装
        String result = "[" + trimmed + "]";
        System.out.println("*** JSON其他格式包装为数组: " + result);
        return result;
    }

    /**
     * 出站多条拆分（结合Apex逻辑）：
     * - 对 AccountToSAP / AccountToSAPUpdate / FinAccountToSAP：
     *   - COMPANY: 若字段 BUKRS 为数组，则拆成多条，每条取一个BUKRS
     *   - SALES: 若 VKORG/VTWEG 为数组，生成笛卡尔积组合；当 VKORG=='5000' 时，置 ALAND='HK'
     */
    private static void applyOutboundSplitting(String interfaceName, JSONArray resultArr, Map<String, Map<String, Object>> valueMapping) {
        if (resultArr == null || resultArr.isEmpty()) return;
        if (!"AccountToSAP".equals(interfaceName)
                && !"AccountToSAPUpdate".equals(interfaceName)
                && !"FinAccountToSAP".equals(interfaceName)) {
            return;
        }

        for (int idx = 0; idx < resultArr.size(); idx++) {
            Object rec = resultArr.get(idx);
            if (!(rec instanceof JSONObject)) continue;
            JSONObject mappedRecord = (JSONObject) rec;

            // COMPANY 拆分
            Object companyObj = mappedRecord.get("COMPANY");
            if (companyObj instanceof JSONArray) {
                JSONArray companyArr = (JSONArray) companyObj;
                JSONArray newCompanyArr = new JSONArray();
                for (int i = 0; i < companyArr.size(); i++) {
                    JSONObject item = companyArr.getJSONObject(i);
                    Object bukrsVal = item.get("BUKRS");
                    if (bukrsVal instanceof JSONArray) {
                        List<String> bukrsList = extractCodes(bukrsVal);
                        for (String b : bukrsList) {
                                JSONObject clone = JSON.parseObject(item.toJSONString()); // 深拷贝
                                clone.put("BUKRS", b);                                    // 只改克隆
                                newCompanyArr.add(clone);
                        }
                    }
                    System.out.println(item);
                }
               if (!newCompanyArr.isEmpty()) {
                   mappedRecord.put("COMPANY", newCompanyArr);
               }
            }

            // SALES 拆分（VKORG x VTWEG 笛卡尔集），并设置 ALAND
            Object salesObj = mappedRecord.get("SALES");
            if (salesObj instanceof JSONArray) {
                JSONArray salesArr = (JSONArray) salesObj;
                JSONArray newSalesArr = new JSONArray();
                for (int i = 0; i < salesArr.size(); i++) {
                    JSONObject item = salesArr.getJSONObject(i);
                    List<String> vkorgList = toStringList(item.get("VKORG"));
                    List<String> vtwegList = toStringList(item.get("VTWEG"));
                    // 去重保持顺序
                    vkorgList = new ArrayList<>(new LinkedHashSet<>(vkorgList));
                    vtwegList = new ArrayList<>(new LinkedHashSet<>(vtwegList));

                    if (!vkorgList.isEmpty() || !vtwegList.isEmpty()) {
                        if (vkorgList.isEmpty()) vkorgList = Collections.singletonList(String.valueOf(item.get("VKORG")));
                        if (vtwegList.isEmpty()) vtwegList = Collections.singletonList(String.valueOf(item.get("VTWEG")));

                        for (String vk : vkorgList) {
                            for (String vt : vtwegList) {
                                JSONObject clone = JSON.parseObject(item.toJSONString()); // 深拷贝
                                String vkMapped = mapByField(valueMapping, "VKORG", vk);
                                String vtMapped = mapByField(valueMapping, "VTWEG", vt);
                                clone.put("VKORG", vkMapped);
                                clone.put("VTWEG", vtMapped);
                                if ("5000".equals(vkMapped)) {
                                    clone.put("ALAND", "HK");
                                }
                                newSalesArr.add(clone);
                            }
                        }
                    } else {
                        // 无数组，原样保留并处理 ALAND 规则
                        String vkMapped = mapByField(valueMapping, "VKORG", String.valueOf(item.get("VKORG")));
                        item.put("VKORG", vkMapped);
                        String vtMapped = mapByField(valueMapping, "VTWEG", String.valueOf(item.get("VTWEG")));
                        item.put("VTWEG", vtMapped);
                        if ("5000".equals(vkMapped)) {
                            item.put("ALAND", "HK");
                        }
                        newSalesArr.add(item);
                    }
                }
                if (!newSalesArr.isEmpty()) {
                    mappedRecord.put("SALES", newSalesArr);
                }
            }
        }
    }

    private static List<String> toStringList(Object val) {
        List<String> list = new ArrayList<>();
        if (val instanceof JSONArray) {
            JSONArray arr = (JSONArray) val;
            for (int i = 0; i < arr.size(); i++) {
                Object e = arr.get(i);
                if (e != null) list.add(String.valueOf(e));
            }
        } else if (val instanceof String) {
            String raw = (String) val;
            if (raw != null) {
                String[] parts = raw.split("[;,，、]+");
                for (String p : parts) {
                    String t = p == null ? "" : p.trim();
                    if (!t.isEmpty()) list.add(t);
                }
            }
        }
        return list;
    }

    private static String mapByField(Map<String, Map<String, Object>> valueMapping, String mappingField, String raw) {
        if (valueMapping == null || mappingField == null) return raw;
        Map<String, Object> map = valueMapping.get(mappingField);
        if (map == null) return raw;
        Object mv = map.get(raw);
        return mv == null ? raw : String.valueOf(mv);
    }

    // 存在映射则映射，否则保持原值，避免不同选项被映射成同一个值
    private static String mapByFieldOptional(Map<String, Map<String, Object>> valueMapping, String mappingField, String raw) {
        if (valueMapping == null || mappingField == null) return raw;
        Map<String, Object> map = valueMapping.get(mappingField);
        if (map == null) return raw;
        Object mv = map.get(raw);
        return mv == null ? raw : String.valueOf(mv);
    }

    // 从字符串/数组中提取编码（优先取空格前的数字段，例如 "1000 公司A" -> "1000"）
    private static List<String> extractCodes(Object val) {
        List<String> list = new ArrayList<>();
        List<String> tokens = toStringList(val);
        for (String t : tokens) {
            String code = t;
            int space = t.indexOf(' ');
            if (space > 0) {
                code = t.substring(0, space);
            }
            list.add(code);
        }
        return list;
    }

    // 注意：按业务需要保留拆分后的条数，即使值相同也不去重
    /**
     * 获取主对象与子对象的关系（如多级主子表、外键递归）
     * @param interfaceName 接口唯一名
     * @param targetObj 目标对象名
     * @return Map<子对象名, 关系描述字符串>
     */
    public static Map<String, String> getChildRelationshipFromSXY(String interfaceName, String targetObj) throws ApiEntityServiceException {
        // 这里可根据实际表结构和业务需要调整
        // 示例：查找所有与targetObj有关联的子对象及其关系字段
        List<FieldMapping> mappings = getFieldMappingsFromSXY(interfaceName, "outbound",null);
        Map<String, String> result = new HashMap<>();
        for (FieldMapping mapping : mappings) {
            if (mapping.sfdcParentObject != null && mapping.sfdcParentObject.equalsIgnoreCase(targetObj) && mapping.sfdcObject != null) {
                // 关系描述可自定义，如 "Account->IT_BANK: Account.IT_BANK__r"
                result.put(mapping.sfdcObject, mapping.sfdcParentObject + "." + mapping.sfdcObject);
            }
        }
        return result;
    }
    /**
     * 全量JSON值替换（根据映射表批量替换JSON中的值）
     * @param json 原始JSON字符串
     * @param replaceMap 替换映射（key=原值，value=新值）
     * @return 替换后的JSON字符串
     */
    public static String replaceJsonValues(String json, Map<String, String> replaceMap) {
        String result = json;
        for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 复杂类型格式化与转换
     * @param value 原始值
     * @param targetType 目标类型（如 String、Integer、Date、Boolean 等）
     * @param format 格式化字符串（如日期格式）
     * @return 转换后的值
     */
    public static Object formatAndConvertValue(Object value, String targetType, String format) {
        if (value == null) return null;
        try {
            if ("String".equalsIgnoreCase(targetType)) {
                return value.toString();
            } else if ("Integer".equalsIgnoreCase(targetType)) {
                return Integer.valueOf(value.toString());
            } else if ("Long".equalsIgnoreCase(targetType)) {
                return Long.valueOf(value.toString());
            } else if ("Double".equalsIgnoreCase(targetType)) {
                return Double.valueOf(value.toString());
            } else if ("Boolean".equalsIgnoreCase(targetType)) {
                return Boolean.valueOf(value.toString());
            } else if ("Date".equalsIgnoreCase(targetType)) {
                if (format != null && !format.isEmpty()) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(format);
                    return sdf.parse(value.toString());
                } else {
                    return java.sql.Date.valueOf(value.toString());
                }
            } else if ("Timestamp".equalsIgnoreCase(targetType)) {
                if (format != null && !format.isEmpty()) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(format);
                    return new java.sql.Timestamp(sdf.parse(value.toString()).getTime());
                } else {
                    return java.sql.Timestamp.valueOf(value.toString());
                }
            }
        } catch (Exception e) {
            // 转换失败返回原值
            return value;
        }
        return value;
    }

    /**
     * 发送接口请求（完整流程）
     * @param interfaceName 接口唯一名
     * @param idList 需要发送的数据ID列表
     * @param direction 方向（inbound/outbound）
     * @return 接口响应结果
     */
    public static String sendInterfaceRequest(String interfaceName, List<String> idList, String direction) throws ApiEntityServiceException {
        // 1. 获取接口配置
        InterfaceConfig config = getInterfaceConfigFromSXY(interfaceName);
        if (config == null) {
            throw new ApiEntityServiceException("未找到接口配置: " + interfaceName);
        }

        // 2. 生成发送数据
        String requestData = generateSendDataXmlString(interfaceName, idList, direction,config); // 自动选择
        System.out.println("请求体："+ requestData);
        // 3. 记录请求日志
        Event_Log__c eventLog__c = new Event_Log__c();
        eventLog__c.setInterface_Config__c(config.id);
        eventLog__c.setBody_Content__c(requestData);
        eventLog__c.setHeader_Content__c(config.interfaceActionUrl);
        eventLog__c.setCall_Time__c(System.currentTimeMillis());
        Long logId = insertEventLog(eventLog__c);
        try {
            // 4. 发送HTTP请求
            String response = sendHttpRequest(config, requestData);
            System.out.println("请求返回："+response);
            // 5. 更新日志状态
//            JSONObject updateLog = new JSONObject();
            Event_Log__c updateLog = new Event_Log__c();
            updateLog.setId(logId);
            updateLog.setReturn_Result__c(response);
            //4.1 判断接口是否成功根据 config.successFlag 来判断
            if (config.successFlag != null && !config.successFlag.isEmpty()){
                boolean contains = response.contains(config.successFlag);
                if (contains) {
                    updateLog.setResult__c(1);
                }else {
                    updateLog.setResult__c(2);
                }
            }
            updateEventLog(updateLog);
            return response;
        } catch (Exception e) {
            Event_Log__c updateLog = new Event_Log__c();
            updateLog.setId(logId);
            updateLog.setError_Content__c(e.getMessage());
            updateLog.setResult__c(2);
            updateEventLog(updateLog);
            throw new ApiEntityServiceException("接口发送失败: " + e.getMessage(), e);
        }
    }

    public static void updateEventLog(Event_Log__c updateLog) {
        try {
            OperateResult update = XObjectService.instance().update(updateLog,true);
        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @Description: 记录日志
     * @Param: [eventLog__c]
     * @return: java.lang.String
     * @Author: 武于伦
     * @email: 2717718875@qq.com
     * @Date: 2025/8/5
     */
    public static Long insertEventLog(Event_Log__c eventLog__c) {
        OperateResult insert = null;
        try {
            Long busiType = MetadataService.instance().getBusiType("event_Log__c", "defaultBusiType").getId();
            eventLog__c.setEntityType(busiType);
            insert = XObjectService.instance().insert(eventLog__c, true);
            System.out.println(JSON.toJSONString(insert));
        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }
        return insert.getDataId();
    }

    /**
     * 发送HTTP请求
     * @param config 接口配置
     * @param requestData 请求数据
     * @return 响应结果
     */
    private static String sendHttpRequest(InterfaceConfig config, String requestData) throws Exception {
        // 使用销售易的 CommonHttpClient

        CommonHttpClient commonHttpClient = CommonHttpClient.instance();

        // 构建请求数据
        CommonData.Builder builder = CommonData.newBuilder()
                .callType(config.httpMethod != null ? config.httpMethod.toUpperCase() : "POST")
                .header("Content-Type", "application/xml") // XML格式
                .callString(config.interfaceActionUrl != null ? config.interfaceActionUrl : "")
                .body(requestData);

        // 设置认证头（如果有token）
        if (config.apiToken != null && !config.apiToken.trim().isEmpty()) {
            builder.header("Authorization", config.apiToken);
        }

        // 设置其他自定义头
        if (config.httpHeaders != null && !config.httpHeaders.isEmpty()) {
            for (Map.Entry<String, String> header : config.httpHeaders.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
        }

        CommonData commonData = builder.build();

        // 执行请求
        HttpResult result = commonHttpClient.execute(commonData);

        if (result != null && result.getResult() != null) {
            return result.getResult();
        } else {
            throw new Exception("HTTP请求失败: " + (result != null ? result.getResult() : "未知错误"));
        }
    }

    /**
     * 通用多层级数据匹配逻辑
     * 支持任意层级的父子关系匹配
     * @param mapping 当前处理的映射配置
     * @param subMapping 要匹配的字段映射配置
     * @return 是否匹配
     */
    private static boolean isMappingObjectMatch(FieldMapping mapping, FieldMapping subMapping) {
        // 1. 直接匹配：mappingObject相同
        if (mapping.mappingObject != null && mapping.mappingObject.equals(subMapping.mappingObject)) {
            return true;
        }

        // 2. 都为空的情况
        if (mapping.mappingObject == null && subMapping.mappingObject == null) {
            return true;
        }

        // 3. 特殊处理：当mapping的mappingObject为jsondata且subMapping的mappingObject为空时，
        // 且它们映射到同一个SFDC对象，认为是匹配的
        if (mapping.mappingObject != null && "jsondata".equals(mapping.mappingObject) &&
                (subMapping.mappingObject == null || subMapping.mappingObject.trim().isEmpty()) &&
                mapping.sfdcObject != null && subMapping.sfdcObject != null &&
                mapping.sfdcObject.equals(subMapping.sfdcObject)) {
            return true;
        }

        // 3. List类型匹配：mappingField作为容器名
        if (mapping.mappingFieldType != null && "List".equalsIgnoreCase(mapping.mappingFieldType)
                && mapping.mappingField != null && mapping.mappingField.equals(subMapping.mappingObject)) {
            return true;
        }

        // 4. 多层级匹配：mappingField作为子对象名
        if (mapping.mappingField != null && mapping.mappingField.equals(subMapping.mappingObject)) {
            return true;
        }

        // 5. 层级关系匹配：检查是否存在父子关系
        if (isParentChildRelationship(mapping, subMapping)) {
            return true;
        }

        // 6. 反向层级关系匹配：检查是否存在子父关系
        if (isChildParentRelationship(mapping, subMapping)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否存在父子关系
     * 例如：ORDERITEM__C.ORDERPLANITEM__C 与 ORDERPLANITEM__C.JHHZD
     * @param parentMapping 父级映射
     * @param childMapping 子级映射
     * @return 是否存在父子关系
     */
    private static boolean isParentChildRelationship(FieldMapping parentMapping, FieldMapping childMapping) {
        // 父级有mappingObject和mappingField，子级有mappingObject
        if (parentMapping.mappingObject != null && parentMapping.mappingField != null &&
                childMapping.mappingObject != null) {
            // 检查父级的mappingField是否等于子级的mappingObject
            return parentMapping.mappingField.equals(childMapping.mappingObject);
        }
        return false;
    }

    /**
     * 检查是否存在子父关系
     * 例如：ORDERPLANITEM__C.JHHZD 与 ORDERITEM__C.ORDERPLANITEM__C
     * @param childMapping 子级映射
     * @param parentMapping 父级映射
     * @return 是否存在子父关系
     */
    private static boolean isChildParentRelationship(FieldMapping childMapping, FieldMapping parentMapping) {
        // 子级有mappingObject，父级有mappingObject和mappingField
        if (childMapping.mappingObject != null &&
                parentMapping.mappingObject != null && parentMapping.mappingField != null) {
            // 检查子级的mappingObject是否等于父级的mappingField
            return childMapping.mappingObject.equals(parentMapping.mappingField);
        }
        return false;
    }

    /**
     * SFDC对象匹配逻辑
     * 支持多种SFDC对象匹配方式
     * @param listObjectName 当前处理的列表对象名
     * @param subMapping 字段映射配置
     * @return 是否匹配
     */
    private static boolean isSfdcObjectMatch(String listObjectName, FieldMapping subMapping) {
        // 1. 直接匹配sfdcObject
        if (subMapping.sfdcObject != null && !subMapping.sfdcObject.trim().isEmpty()) {
            if (listObjectName.equals(subMapping.sfdcObject)) {
                return true;
            }
        }

        // 2. 匹配sfdcDetailObject
        if (subMapping.sfdcDetailObject != null && !subMapping.sfdcDetailObject.trim().isEmpty()) {
            if (listObjectName.equals(subMapping.sfdcDetailObject)) {
                return true;
            }
        }

        // 3. 模糊匹配：忽略大小写和下划线
        String normalizedListName = normalizeObjectName(listObjectName);
        if (subMapping.sfdcObject != null && !subMapping.sfdcObject.trim().isEmpty()) {
            String normalizedSfdcObject = normalizeObjectName(subMapping.sfdcObject);
            if (normalizedListName.equals(normalizedSfdcObject)) {
                return true;
            }
        }

        if (subMapping.sfdcDetailObject != null && !subMapping.sfdcDetailObject.trim().isEmpty()) {
            String normalizedSfdcDetailObject = normalizeObjectName(subMapping.sfdcDetailObject);
            if (normalizedListName.equals(normalizedSfdcDetailObject)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 标准化对象名称
     * 统一处理大小写和下划线
     * @param objectName 对象名称
     * @return 标准化后的名称
     */
    private static String normalizeObjectName(String objectName) {
        if (objectName == null) {
            return "";
        }
        return objectName.trim().toLowerCase().replace("__c", "");
    }

    public static void main(String[] args) throws ApiEntityServiceException {
//        System.out.println("\n=== 测试接口请求 ===");
        List<String> idList = Arrays.asList("3955686802015266"); // 3924062138614808/3900668407006226
        CommoninterfaceUtil.sendInterfaceRequest("JST_CRM_PROJECT", idList, "outbound");//Inquiry /SupplierToSAP

    }
}
