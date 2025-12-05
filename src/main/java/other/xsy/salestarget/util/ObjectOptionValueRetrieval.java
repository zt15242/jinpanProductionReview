package other.xsy.salestarget.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import com.rkhd.platform.sdk.http.handler.ResponseBodyHandlers;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 武于伦
 * @version 1.0
 * @date 2024/11/26 10:11
 * @e-mail 2717718875@qq.com
 * @description:
 */
public class ObjectOptionValueRetrieval {
    private static final Logger logger = LoggerFactory.getLogger();
    private static final Integer RESULT_CODE = 200;
    private static final Map<String, Map<String, Map<String, String>>> optionsCache = new ConcurrentHashMap<>();

    /**
     * 获取对象所有选项字段的映射
     * @param objectApiKey 对象api 例如：商品：goods
     * @return Map<字段API, Map<选项标签, 选项值>>
     */
    public static Map<String, Map<String, String>> getAllFieldOptions(String objectApiKey) {
        ensureOptionsLoaded(objectApiKey);
        return optionsCache.getOrDefault(objectApiKey, new HashMap<>());
    }

    /**
     * 获取单个字段的选项值映射
     * @param objectApiKey 对象api 例如：商品：goods
     * @param fieldApiKey 字段api
     * @return Map<选项标签, 选项值>
     */
    public static Map<String, String> objectInformation(String objectApiKey, String fieldApiKey) {
        Map<String, Map<String, String>> allOptions = getAllFieldOptions(objectApiKey);
        return allOptions.getOrDefault(fieldApiKey, new HashMap<>());
    }

    private static synchronized void ensureOptionsLoaded(String objectApiKey) {
        if (!optionsCache.containsKey(objectApiKey)) {
            loadOptions(objectApiKey);
        }
    }

    private static void loadOptions(String objectApiKey) {
        Map<String, Map<String, String>> fieldOptions = new HashMap<>();
        
        try {
            RkhdHttpClient client = RkhdHttpClient.instance();
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString("/rest/data/v2.0/xobjects/" + objectApiKey + "/description");
            JSONObject responseObject = client.execute(data, ResponseBodyHandlers.ofJSON());
            
            if (!RESULT_CODE.equals(responseObject.getIntValue("code"))) {
                logger.error("获取对象描述失败: " + responseObject.getString("message"));
                return;
            }

            String entityStr = responseObject.getString("data");
            if (entityStr == null) {
                logger.error("返回数据为空");
                return;
            }

            JSONObject entity = JSONObject.parseObject(entityStr);
            JSONArray fields = entity.getJSONArray("fields");
            
            for (int i = 0; i < fields.size(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String fieldApiKey = field.getString("label");
                Map<String, String> optionMap = new HashMap<>();
                boolean hasOptions = false;

                // 处理selectitem
                JSONArray selectitem = field.getJSONArray("selectitem");
                if (selectitem != null && !selectitem.isEmpty()) {
                    hasOptions = true;
                    for (int j = 0; j < selectitem.size(); j++) {
                        JSONObject option = selectitem.getJSONObject(j);
                        String label = option.getString("value");
                        String value = option.getString("label");
                        optionMap.put(label, value);
                        logger.info("字段[" + fieldApiKey + "]选项值: " + label + " -> " + value);
                    }
                }

                // 处理checkitem
                JSONArray checkitem = field.getJSONArray("checkitem");
                if (checkitem != null && !checkitem.isEmpty()) {
                    hasOptions = true;
                    for (int k = 0; k < checkitem.size(); k++) {
                        JSONObject option = checkitem.getJSONObject(k);
                        String label = option.getString("value");
                        String value = option.getString("label");
                        optionMap.put(label, value);
                        logger.info("字段[" + fieldApiKey + "]选项值: " + label + " -> " + value);
                    }
                }

                // 只保存有选项值的字段
                if (hasOptions) {
                    fieldOptions.put(fieldApiKey, optionMap);
                }
            }

            // 将结果放入缓存
            optionsCache.put(objectApiKey, fieldOptions);
            logger.info("成功加载对象[" + objectApiKey + "]的选项值配置，共 " + fieldOptions.size() + " 个选项字段");

        } catch (Exception e) {
            logger.error("加载选项值失败: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) throws IOException {
        // 获取所有选项字段映射
        Map<String, Map<String, String>> allOptions = getAllFieldOptions("goods");
        
        // 获取特定字段的选项值
        Map<String, String> materialTypeOptions = allOptions.get("factory__c");
        if (materialTypeOptions == null) {
            String value = materialTypeOptions.get("7020");
            System.out.println("Material_Type__c 字段的 'Z003' 选项值: " + value);
        }
        
        // 打印所有选项字段及其值
        allOptions.forEach((field, options) -> {
            System.out.println("\n字段: " + field);
            options.forEach((label, value) -> 
                System.out.println("  " + label + " -> " + value));
        });
    }
}
