package other.xsy.caseworktime.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Receipt_and_Inspection_Note__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import com.rkhd.platform.sdk.http.handler.ResponseBodyHandlers;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XObjectService;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class NeoCrmRkhdService {
    private static final Logger logger = LoggerFactory.getLogger();

    /**
    * @Description: 获取角色用户
    * @Param: [client, apikey]
    * @return: com.alibaba.fastjson.JSONObject
    * @Author: 武于伦
    * @email: 2717718875@qq.com
    * @Date: 2025/7/31
    */
    public static JSONArray roleUsers(RkhdHttpClient client, String apikey) throws IOException {
        JSONArray records = new JSONArray();
        try {
            if (client==null){
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/metadata/v2.0/privileges/roles/"+apikey+"/actions/users");
            data.setCall_type("GET");
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            if (StringUtils.isNotBlank(responseStr)) {
                logger.info("查询列表结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (responseCode.equals(0)) {
                    String resultStr = responseObject.getString("data");
                    JSONObject dataJson = JSONObject.parseObject(resultStr);
                    records = dataJson.getJSONArray("records");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return records;
    }
    /***
    * @Description: 获取指定职能用户
    * @Param: [client, apikey]
    * @return: com.alibaba.fastjson.JSONArray
    * @Author: 武于伦
    * @email: 2717718875@qq.com
    * @Date: 2025/9/4
    */
    public static JSONArray functionsUsers(RkhdHttpClient client, String apikey) throws IOException {
        JSONArray records = new JSONArray();
        try {
            if (client==null){
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/metadata/v2.0/privileges/responsibility/"+apikey+"/actions/users");
            data.setCall_type("GET");
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            if (StringUtils.isNotBlank(responseStr)) {
                logger.info("查询列表结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (responseCode.equals(0)) {
                    String resultStr = responseObject.getString("data");
                    JSONObject dataJson = JSONObject.parseObject(resultStr);
                    records = dataJson.getJSONArray("records");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return records;
    }
    /**
    * @Description: 发布工作圈
    * @Param: [client, objectId, content]
    * @return: com.alibaba.fastjson.JSONObject
    * @Author: 武于伦
    * @email: 2717718875@qq.com
    * @Date: 2025/7/31
    */
    public static JSONObject workingCircle(RkhdHttpClient client, Long objectId, String content) throws IOException {
        JSONObject jsonData = new JSONObject();
        jsonData.put("objectId",objectId);
        jsonData.put("content",content);
        JSONObject dataJson = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/social/story");
            data.setCall_type("POST");
            data.setBody(jsonData.toJSONString());
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            if (StringUtils.isNotBlank(responseStr)) {
                logger.info("请求返回结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (responseCode.equals(200)) {
                    String resultStr = responseObject.getString("result");
                    dataJson = JSONObject.parseObject(resultStr);
                }
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return dataJson;
    }

   /***
   * @Description: 更新或者新增
   * @Param: [client, xObject, onlyValue, partialSuccess, admin]
   * @return: com.rkhd.platform.sdk.model.OperateResult
   * @Author: 武于伦
   * @email: 2717718875@qq.com
   * @Date: 2025/9/1
   */
    public static OperateResult upsert(RkhdHttpClient client, XObject xObject,String onlyValue, boolean partialSuccess, boolean admin) throws IOException {
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            String apiKey = xObject.getApiKey();
            // 获取xObject中onlyValue字段对应的值
            Object fieldValue = xObject.getAttribute(onlyValue);
            if (fieldValue == null) {
                throw new RuntimeException("字段 " + onlyValue + " 的值为空，无法执行upsert操作");
            }
            
            // 通过唯一字段查询
            //1、拼写sql - 使用字符串拼接，因为XObjectService.query不支持参数绑定
            String sql = "select id,"+onlyValue+" from "+apiKey+" where "+onlyValue+"='" + fieldValue + "'";
            // 执行查询
            QueryResult<XObject> query = XObjectService.instance().query(sql, partialSuccess, admin);
            
            // 如果查询到记录，执行更新操作
            if (query != null && query.getRecords() != null && !query.getRecords().isEmpty()) {
                // 获取第一条记录的ID
                XObject existingRecord = query.getRecords().get(0);
                Long recordId = existingRecord.getId();
                
                // 设置ID并执行更新
                xObject.setId(recordId);
                logger.info("执行更新操作，记录ID: " + recordId);
                return XObjectService.instance().update(xObject,admin);
            } else {
                // 如果没有查询到记录，执行插入操作
                logger.info("执行插入操作，字段 " + onlyValue + " 的值: " + fieldValue);
                return XObjectService.instance().insert(xObject,admin);
            }
            
        }catch (Exception e){
            logger.error("upsert操作失败: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    public static <T extends XObject> Map<String, Long> upsert(RkhdHttpClient client, List<T> xObjectList, String onlyValue, boolean partialSuccess, boolean admin) throws IOException {
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            
            if (xObjectList == null || xObjectList.isEmpty()) {
                throw new RuntimeException("XObject列表为空，无法执行upsert操作");
            }
            
            // 获取第一个对象的apiKey，假设所有对象都是同一类型
            String apiKey = xObjectList.get(0).getApiKey();
            
            // 批量处理每个XObject对象
            Map<String, Long> onlyValueToId = new HashMap<String, Long>();
            int successCount = 0;
            int totalCount = xObjectList.size();
            
            for (T xObject : xObjectList) {
                try {
                    // 获取xObject中onlyValue字段对应的值
                    Object fieldValue = xObject.getAttribute(onlyValue);
                    if (fieldValue == null) {
                        logger.warn("对象 " + xObject + " 的字段 " + onlyValue + " 的值为空，跳过此对象");
                        continue;
                    }
                    
                    // 通过唯一字段查询
                    String sql = "select id," + onlyValue + " from " + apiKey + " where " + onlyValue + "='" + fieldValue + "'";
                    
                    // 执行查询
                    QueryResult<XObject> query = XObjectService.instance().query(sql, partialSuccess, admin);
                    
                    // 如果查询到记录，执行更新操作
                    if (query != null && query.getRecords() != null && !query.getRecords().isEmpty()) {
                        // 获取第一条记录的ID
                        XObject existingRecord = query.getRecords().get(0);
                        Long recordId = existingRecord.getId();
                        
                        // 设置ID并执行更新
                        xObject.setId(recordId);
                        logger.info("执行更新操作，记录ID: " + recordId + ", 字段值: " + fieldValue);
                        OperateResult updateResult = XObjectService.instance().update(xObject,admin);
                        if (updateResult != null && updateResult.getSuccess()) {
                            successCount++;
                            onlyValueToId.put(String.valueOf(fieldValue), recordId);
                        }
                    } else {
                        // 如果没有查询到记录，执行插入操作
                        logger.info("执行插入操作，字段 " + onlyValue + " 的值: " + fieldValue);
                        OperateResult insertResult = XObjectService.instance().insert(xObject,admin);
                        if (insertResult != null && insertResult.getSuccess()) {
                            successCount++;
                            // 插入后再次查询以获取新记录ID
                            QueryResult<XObject> afterInsertQuery = XObjectService.instance().query(sql, partialSuccess, admin);
                            if (afterInsertQuery != null && afterInsertQuery.getRecords() != null && !afterInsertQuery.getRecords().isEmpty()) {
                                Long newId = afterInsertQuery.getRecords().get(0).getId();
                                onlyValueToId.put(String.valueOf(fieldValue), newId);
                            } else {
                                logger.warn("插入后未能通过唯一字段查询到记录，字段值: " + fieldValue);
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    logger.error("处理对象时发生错误: " + e.getMessage(), e);
                    if (!partialSuccess) {
                        throw new RuntimeException("处理对象失败: " + e.getMessage(), e);
                    }
                    // 如果允许部分成功，继续处理下一个对象
                }
            }
            
            logger.info("批量upsert操作完成，成功处理 " + successCount + "/" + totalCount + " 个对象");
            
            // 返回唯一字段到ID的映射
            return onlyValueToId;
            
        } catch (Exception e) {
            logger.error("批量upsert操作失败: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static OperateResult campaignUpsert(RkhdHttpClient client, XObject xObject,String onlyValue, boolean partialSuccess, boolean admin) throws IOException {
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            String apiKey = xObject.getApiKey();
            // 获取xObject中onlyValue字段对应的值
            Object fieldValue = xObject.getAttribute(onlyValue);
            if (fieldValue == null) {
                throw new RuntimeException("字段 " + onlyValue + " 的值为空，无法执行upsert操作");
            }

            // 通过唯一字段查询
            //1、拼写sql - 使用字符串拼接，因为XObjectService.query不支持参数绑定
            String sql = "select id,status,finishReasonDescription__c,finishReason__c,"+onlyValue+" from "+apiKey+" where "+onlyValue+"='" + fieldValue + "'";
            // 执行查询
            QueryResult<XObject> query = XObjectService.instance().query(sql, partialSuccess, admin);

            // 如果查询到记录，执行更新操作
            if (query != null && query.getRecords() != null && !query.getRecords().isEmpty()) {
                // 获取第一条记录的ID
                XObject existingRecord = query.getRecords().get(0);
                Long recordId = existingRecord.getId();
                Integer status = existingRecord.getAttribute("status");
                String finishReasonDescription__c = existingRecord.getAttribute("finishReasonDescription__c");
                String finishReason__c = existingRecord.getAttribute("finishReason__c");
                if (status == 4 && (finishReasonDescription__c == null || finishReason__c.isEmpty())) {
                    throw new RemoteException("请填写中止原因以及详细说明");
                }
                // 设置ID并执行更新
                xObject.setId(recordId);
                logger.info("执行更新操作，记录ID: " + recordId);
                return XObjectService.instance().update(xObject,admin);
            } else {
                // 如果没有查询到记录，执行插入操作
                logger.info("执行插入操作，字段 " + onlyValue + " 的值: " + fieldValue);
                return XObjectService.instance().insert(xObject,admin);
            }

        }catch (Exception e){
            logger.error("upsert操作失败: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    public static void main(String[] args) throws ApiEntityServiceException, IOException {
        Receipt_and_Inspection_Note__c receiptAndInspectionNote__c = new Receipt_and_Inspection_Note__c();
        receiptAndInspectionNote__c.setDeliveryNo__c("11112");
        Long id = MetadataService.instance().getBusiType("receipt_and_Inspection_Note__c", "defaultBusiType").getId();
        receiptAndInspectionNote__c.setEntityType(id);
        OperateResult upsert = upsert(null, receiptAndInspectionNote__c, "deliveryNo__c", true, true);
    }
}
