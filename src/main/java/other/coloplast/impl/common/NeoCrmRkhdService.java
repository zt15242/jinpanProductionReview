package other.coloplast.impl.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.exception.XsyHttpException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import com.rkhd.platform.sdk.http.handler.ResponseBodyHandlers;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import org.apache.commons.lang.StringUtils;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/**
 * @author SIMON
 * @title: NeoCrmRkhdService
 * @projectName
 * @description: TODO
 * @date 2021/11/13 11:11
 */
public class NeoCrmRkhdService {
    private static final Logger logger = LoggerFactory.getLogger();
    private static final Integer RESULT_CODE = 200;
    /**
     * query最大sql长度
     */
    private final static int QUERY_LENGTH = 900;
    /**
     * query预留长度
     */
    private final static int QUERY_OBL_LENGTH = 50;
    /**
     * V2最大查询长度
     */
    private static final int V2_LENGTH = 300;

    /**
     * 获取工商信息接口
     *
     * @param companyName 公司全称
     * @return 工商信息JSON
     */
    public static JSONObject rtnEnterpriseInfo(RkhdHttpClient client, String companyName) throws Exception {
        if (client == null) {
            client = RkhdHttpClient.instance();
        }
        JSONObject enterpriseInfo = null;
        RkhdHttpData data = new RkhdHttpData();
        data.setCall_type("GET");
        data.setCallString("/data/v1/objects/enterprise/info?name=" + companyName.replaceAll("\\s", "%20"));
        try {
            JSONObject resJson = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (resJson.containsKey("error_code")) {
                logger.error("获取工商信息出错:" + resJson.getString("error_code"));
//                if(resJson.getString("error_code").equals("20000002")) {
//                    client = RkhdHttpClient.instance();
//                    rtnEnterpriseInfo(client, companyName);
//                }
                //  XxlJobHelper.log("公司名称："+companyName+"获取工商信息出错:" + resJson.getString("error_code"));
            } else {
                JSONObject rstJson = resJson != null ? resJson.getJSONObject("result") : null;

                //if(rstJson!=null&&rstJson.getInteger("flag")==1)
                if (rstJson != null) {
                    //property2如果不为空，名称是一个曾用名,按工商不存在处理
//                    logger.info("property2:" + rstJson.getString("property2"));
//                    logger.info(""+StringUtils.isBlank(rstJson.getString("property2")));
                    if (StringUtils.isBlank(rstJson.getString("property2"))) {
                        enterpriseInfo = rstJson;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            //XxlJobHelper.log("公司名称："+companyName+"调用工商信息接口出错:" + e.getMessage());
        }
        return enterpriseInfo;
    }
    /**
     * 根据ApiKey(对象名称)获取有效的id,apikey的map
     * kk add
     * @param apiKey
     *
     */
    public static HashMap<Long,String> getEntityIdAndTypeMap(RkhdHttpClient client, String apiKey) {
        HashMap<Long,String> map = new HashMap<>();
        try {
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/" + apiKey + "/busiType").build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }
            for (Object obj : result.getJSONObject("data").getJSONArray("records")) {
                JSONObject jsonObject = (JSONObject) obj;
                if(jsonObject.getBoolean("active")){
                    map.put(jsonObject.getLong("id"),jsonObject.getString("apiKey"));
                }

            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error->" + e.toString());
        }
        return map;
    }

    /**
     * 获取实体信息Xobject
     *
     * @param client     RkhdHttpClient
     * @param entityName 实体名称
     * @param id         实体记录Id
     * @return JSONObject 实体信息
     */
    public static JSONObject v2QueryRecordInfoByIdXobject(RkhdHttpClient client, String entityName, Long id) {
        logger.info(" GetEntityInfoById：查询实体:" + entityName + "查询ID：" + id);
        JSONObject entity = null;
        try {
            if (client == null) {
                client = new RkhdHttpClient();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString("/rest/data/v2.0/xobjects/" + entityName + "/" + id);
            String responseStr = client.performRequest(data);
            if (StringUtils.isNotBlank(responseStr)) {
                logger.info(" GetEntityInfoById：查询实体信息结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (RESULT_CODE.equals(responseCode)) {
                    String entityStr = responseObject.getString("data");
                    entity = JSONObject.parseObject(entityStr);
                }
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        logger.info(" GetEntityInfoById：返回结果:" + entity);
        return entity;
    }

    /**
     * 根据SQL查询结果集
     *
     * @param client RkhdHttpClient
     * @param sql    查询语句
     * @return 查询结果List
     */
    public static JSONArray v2QueryBySql(RkhdHttpClient client, String sql) {
        logger.info("sql:" + sql);
        JSONArray list = null;
        try {
            if (client == null) {
                client = new RkhdHttpClient();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString("/rest/data/v2/query?q=" + URLEncoder.encode(sql, "utf-8"));
            String responseStr = client.performRequest(data);
            if (StringUtils.isNotBlank(responseStr)) {
                logger.info("查询列表结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (RESULT_CODE.equals(responseCode)) {
                    String resultStr = responseObject.getString("result");
                    JSONObject result = JSONObject.parseObject(resultStr);
                    Long totalSize = result.getLongValue("totalSize");
                    if (totalSize > 0) {
                        String records = result.getString("records");
                        list = JSONArray.parseArray(records);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return list;
    }

    public static JSONArray v2QueryAllRecord(RkhdHttpClient client, String sql){
        return v2QueryAllRecord(client,sql,null,true);
    }

    /**
     * 根据SQL查询结果集
     * @param client
     * @param sql
     * @return
     */
    public static JSONArray v2QueryAllRecord(RkhdHttpClient client, String sql,boolean isAdmin){
        return v2QueryAllRecord(client,sql,null,isAdmin);
    }

    /**
     * 根据SQL查询结果集
     *
     * @param client RkhdHttpClient
     * @param sql    查询语句
     * @return 查询结果List
     */
    public static JSONArray v2QueryAllRecord(RkhdHttpClient client, String sql,String orderBy,boolean isAdmin) {
        int start = 0;
        RkhdHttpData data = new RkhdHttpData();
        String baseUrl = "/rest/data/v2/query?q=";
        logger.info("sql:" + sql);
        if(StringUtils.isNotBlank(orderBy)){
            sql+=" "+orderBy+" ";
        }else{
            sql+=" order by id ";
        }
        // 是否继续读取数据
        boolean hasData = false;
        JSONArray records = new JSONArray();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            do {
                hasData = false;
                String url = baseUrl + URLEncoder.encode(sql + " limit " + start + " , " + V2_LENGTH, "utf-8");
                logger.info("url:" + url);
                data.setCall_type("Get");
                data.setCallString(url);
                setAdminPrivileges(isAdmin,data);
                String result = client.performRequest(data);
                logger.info("result:" + result);
                if (StringUtils.isNotBlank(result)) {
                    JSONObject resultJson = JSONObject.parseObject(result);
                    long code = resultJson.getLong("code");
                    if (code == RESULT_CODE) {
                        JSONObject resultobj = resultJson.getJSONObject("result");
                        int count = resultobj.getIntValue("count");
                        int totalSize = resultobj.getIntValue("totalSize");
                        if (count > 0) {
                            records.addAll(resultJson.getJSONObject("result").getJSONArray("records"));
                        }
                        start = start + V2_LENGTH;
                        if (totalSize > start) {
                            hasData = true;
                        }
                    } else {
                        logger.error(sql + "错误:-->" + code + "|错误原因:" + resultJson.getString("msg"));
                    }
                } else {
                    logger.error(sql + "错误:-->返回为空");
                }
            } while (hasData);
        } catch (Exception e) {
            // TODO: handle exception
            logger.error(" 报错信息：" + e);
        }
        return records;
    }

    /**
     * 获取V2Query结果-全量获取-且拆分WHERE
     *
     * @param client  RkhdHttpClient类型的连接器-null时自动生成
     * @param baseSql 查询语句不带分页,format格式，预留需要添加的where位置
     * @return 返回查询结果字符串
     * @throws IOException
     * @throws ScriptBusinessException
     * @throws ApiEntityServiceException
     */
    public static JSONArray v2QuerySplitIn(RkhdHttpClient client, String baseSql, List<String> whereList) throws IOException, ScriptBusinessException, ApiEntityServiceException {
        JSONArray records = new JSONArray();
        int sqlLength = QUERY_LENGTH - QUERY_OBL_LENGTH - baseSql.length();

        StringBuilder tempSql = new StringBuilder("");
        for (String item : whereList) {
            if ((tempSql.length() + item.length() + 1) > sqlLength) {
                String sql = String.format(baseSql, "(" + tempSql.substring(1) + ")");
                logger.debug("formatSql:" + sql);
                JSONArray array = NeoCrmRkhdService.v2QueryBySql(client, sql);
                if ( array != null) {
                    records.addAll(array);
                }
                tempSql = new StringBuilder("");
            }
            if ("".equals(tempSql)) {
                tempSql = new StringBuilder(item);
            } else {
                tempSql.append(",").append(item);
            }
        }
        if (!"".equals(tempSql)) {
            String sql = String.format(baseSql, "(" + tempSql.substring(1) + ")");
            logger.debug("formatSql:" + sql);
            JSONArray array = NeoCrmRkhdService.v2QueryBySql(client, sql);
            if (array != null) {
                records.addAll(array);
            }
        }
        return records;
    }

    public static boolean v2UpdateEntityXobject(RkhdHttpClient client, String entityName, long id, JSONObject object){
        return v2UpdateEntityXobject(client,entityName,id,object,false);
    }

    /**
     * 修改实体信息
     *
     * @param client     RkhdHttpClient
     * @param entityName 实体名
     * @param id         记录ID
     * @param object     修改纪录的信息
     * @return boolean
     */
    public static boolean v2UpdateEntityXobject(RkhdHttpClient client, String entityName, long id, JSONObject object,boolean isAdmin) {
        boolean result = false;
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/xobjects/" + entityName + "/" + id);
            data.setCall_type("PATCH");
            setAdminPrivileges(isAdmin,data);
            JSONObject record = new JSONObject();
            record.put("data", object);
            logger.info("修改实体信息，实体名：" + entityName + "修改记录ID ：" + id + "，修改内容：" + record.toString());
            data.setBody(record.toString());
            String responseStr = client.performRequest(data);
            logger.info("返回信息：" + responseStr);
            JSONObject responseObject = JSONObject.parseObject(responseStr);
            Integer responseCode = responseObject.getIntValue("code");
            if (RESULT_CODE.equals(responseCode)) {
                result = true;
            } else {
                logger.info("返回信息：" + responseStr);
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return result;
    }


    public static JSONObject v2UpdateEntityXobject2(RkhdHttpClient client, String entityName, long id, JSONObject object,boolean admin) {
        JSONObject responseObject = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/xobjects/" + entityName + "/" + id);
            data.setCall_type("PATCH");
            setAdminPrivileges(admin,data);
            JSONObject record = new JSONObject();
            record.put("data", object);
            logger.info("修改实体信息，实体名：" + entityName + "修改记录ID ：" + id + "，修改内容：" + record.toString());
            data.setBody(record.toString());
            String responseStr = client.performRequest(data);
            logger.info("返回信息：" + responseStr);
            responseObject = JSONObject.parseObject(responseStr);
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return responseObject;
    }

    /**
     * 不过滤json null进行更新，为了清空字段
     * @param client
     * @param entityName
     * @param id
     * @param object
     * @return
     */
    public static JSONObject v2UpdateEntityXobjectForWriteNull(RkhdHttpClient client, String entityName, long id, JSONObject object,boolean admin) {
        JSONObject responseObject = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/xobjects/" + entityName + "/" + id);
            data.setCall_type("PATCH");
            setAdminPrivileges(admin,data);
            JSONObject record = new JSONObject();
            record.put("data", object);
            String bodyStr = JSON.toJSONString(record,  SerializerFeature.WriteMapNullValue);
            logger.info("修改实体信息，实体名：" + entityName + "修改记录ID ：" + id + "，修改内容：" + record.toString());
            data.setBody(bodyStr);
            String responseStr = client.performRequest(data);
            logger.info("返回信息：" + responseStr);
            responseObject = JSONObject.parseObject(responseStr);
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return responseObject;
    }

    private static void setAdminPrivileges(boolean admin, RkhdHttpData data) {
        if (admin) {
            Map<String, String> headerMap = data.getHeaderMap();
            headerMap.put("xsy-criteria", "10");
        }

    }

    /**
     * 创建实体记录Xobject
     *
     * @param client     RkhdHttpClient
     * @param entityName 实体名称
     * @param object     新纪录的信息
     * @return Id
     */
    public static long v2CreateEntityXobject(RkhdHttpClient client, String entityName, JSONObject object) {
        long resultId = 0;
        try {
            if (client == null) {
                client = new RkhdHttpClient();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/xobjects/" + entityName);
            data.setCall_type("POST");
            JSONObject record = new JSONObject();
            record.put("data", object);
            //XxlJobHelper.log("新增实体信息，实体名：" + entityName + "新增内容：" + record.toString());
            logger.info("新增实体信息，实体名：" + entityName + "新增内容：" + record.toString());
            data.setBody(record.toString());
            String responseStr = client.performRequest(data);
            //XxlJobHelper.log("返回信息：" + responseStr);
            logger.info("返回信息：" + responseStr);
            JSONObject responseObject = JSONObject.parseObject(responseStr);
            Integer responseCode = responseObject.getIntValue("code");
            if (RESULT_CODE.equals(responseCode)) {
                JSONObject result = responseObject.getJSONObject("data");
                resultId = result.getLong("id");
            }
        } catch (Exception e) {
            //XxlJobHelper.log(" 报错信息：" + e);
            logger.error(" 报错信息：" + e);
        }
        return resultId;
    }
    /**
     * 创建实体记录Xobject
     *
     * @param client     RkhdHttpClient
     * @param entityName 实体名称
     * @param object     新纪录的信息
     * @return Id
     */
    public static JSONObject insertEntityXobject(RkhdHttpClient client, String entityName, JSONObject object) {
        JSONObject result = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/xobjects/" + entityName);
            data.setCall_type("POST");
            JSONObject record = new JSONObject();
            record.put("data", object);
            //XxlJobHelper.log("新增实体信息，实体名：" + entityName + "新增内容：" + record.toString());
            logger.info("新增实体信息，实体名：" + entityName + "新增内容：" + record.toString());
            data.setBody(record.toString());
            String responseStr = client.execute(data,ResponseBodyHandlers.ofString());
            //XxlJobHelper.log("返回信息：" + responseStr);
            logger.info("返回信息：" + responseStr);
            result = JSONObject.parseObject(responseStr);
        } catch (Exception e) {
            //XxlJobHelper.log(" 报错信息：" + e);
            logger.error(" 报错信息：" + e);
        }
        return result;
    }
    /**
     * V2.0描述接口，获取单选和多选的值字段的键值对
     *
     * @param client RkhdHttpClient
     * @param source "apikey"或者"label"|apikey为单选或者多选配置中的【API名称】|label为单选或者多选配置中的【选项名称】
     * @param apiKey 对象名
     * @param kvMap  (apiKeyName(label:value) ) 或者 (apiKeyName(apikey:value) )
     * @param vkMap  (apiKeyName(value:label) ) 或者 (apiKeyName(value:apikey) )   结果集合和kvMap相反
     * @throws ScriptBusinessException
     */
    public static void getPicklistValue(RkhdHttpClient client, String apiKey, String source, Map<String, Map<String, Integer>> kvMap, Map<String, Map<Integer, String>> vkMap)
            throws ScriptBusinessException {
        try {
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/" + apiKey + "/description").build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }

            for (Object obj : result.getJSONObject("data").getJSONArray("fields")) {
                JSONObject jsonObject = (JSONObject) obj;
                // 将单选类型的描述保存起来
                if ("picklist".equals(jsonObject.getString("type"))) {
                    Map<String, Integer> keyMap = new HashMap<String, Integer>();
                    Map<Integer, String> valueMap = new HashMap<Integer, String>();
                    JSONArray array = jsonObject.getJSONArray("selectitem");
                    for (int i = 0; i < array.size(); i++) {
                        JSONObject object = array.getJSONObject(i);
                        keyMap.put(object.getString(source), object.getInteger("value"));
                        valueMap.put(object.getInteger("value"), object.getString(source));
                    }
                    kvMap.put(jsonObject.getString("apiKey"), keyMap);
                    vkMap.put(jsonObject.getString("apiKey"), valueMap);
                }
                // 将多选类型的描述保存起来
                if ("multipicklist".equals(jsonObject.getString("type"))) {
                    Map<String, Integer> keyMap = new HashMap<String, Integer>();
                    Map<Integer, String> valueMap = new HashMap<Integer, String>();
                    JSONArray array = jsonObject.getJSONArray("checkitem");
                    for (int i = 0; i < array.size(); i++) {
                        JSONObject object = array.getJSONObject(i);
                        keyMap.put(object.getString(source), object.getInteger("value"));
                        valueMap.put(object.getInteger("value"), object.getString(source));
                    }
                    kvMap.put(jsonObject.getString("apiKey"), keyMap);
                    vkMap.put(jsonObject.getString("apiKey"), valueMap);
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error(e.getMessage(), e);
            throw new ScriptBusinessException(e);
        }
    }

    /**
     * 根据ApiKey(对象名称)获取entityType（业务类型）的id
     *
     * @param apiKey
     * @param entityApiKey
     * @return entityTypeId
     */    public static Long getEntityTypesId(RkhdHttpClient client, String apiKey, String entityApiKey) {
        Long entityTypesId = null;
        try {
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/" + apiKey + "/busiType").build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }

            for (Object obj : result.getJSONObject("data").getJSONArray("records")) {
                JSONObject jsonObject = (JSONObject) obj;
                if (entityApiKey.equals(jsonObject.getString("apiKey"))) {
                    entityTypesId = jsonObject.getLong("id");
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error->" + e.toString());
        }
        return entityTypesId;
    }

    /**
     * 根据ApiKey(对象名称)获取有效的apikey和id的map
     * kk add
     * @param apiKey
     *
     */
    public static HashMap<String,Long> getEntityTypesMap(RkhdHttpClient client, String apiKey) {
        HashMap<String,Long> map = new HashMap<>();
        try {
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/" + apiKey + "/busiType").build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }

            for (Object obj : result.getJSONObject("data").getJSONArray("records")) {
                JSONObject jsonObject = (JSONObject) obj;
                if(jsonObject.getBoolean("active")){
                    map.put(jsonObject.getString("apiKey"),jsonObject.getLong("id"));
                }

            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error->" + e.toString());
        }
        return map;
    }

    public static HashMap<String,Long> getEntityTypesMap2(RkhdHttpClient client, String apiKey) {
        HashMap<String,Long> map = new HashMap<>();
        try {
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/" + apiKey + "/busiType").build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }

            for (Object obj : result.getJSONObject("data").getJSONArray("records")) {
                JSONObject jsonObject = (JSONObject) obj;
                if(jsonObject.getBoolean("active")){
                    map.put(jsonObject.getString("label"),jsonObject.getLong("id"));
                }

            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error->" + e.toString());
        }
        return map;
    }

    /**
     * 根据ApiKey(对象名称)获取有效的apikey和id的map
     * sx add
     * @param apiKey
     *
     */
    public static HashMap<Long,String> getEntityTypesMap1(RkhdHttpClient client, String apiKey) {
        HashMap<Long,String> map = new HashMap<>();
        try {
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/" + apiKey + "/busiType").build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }

            for (Object obj : result.getJSONObject("data").getJSONArray("records")) {
                JSONObject jsonObject = (JSONObject) obj;
                if(jsonObject.getBoolean("active")){
                    map.put(jsonObject.getLong("id"),jsonObject.getString("label"));
                }

            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error->" + e.toString());
        }
        return map;
    }


    /**
     * 删除对象数据下所有团队成员
     *
     * @param client
     * @param businessId 业务对象-数据id
     * @param apiKey     业务对象
     */
    public static void deleteAllMembers(RkhdHttpClient client, Long businessId, String apiKey) {
        try {
            if (client == null) {
                client = new RkhdHttpClient();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCallString("/rest/data/v2.0/xobjects/" + apiKey + "/" + businessId + "/teamwork/members");
            data.setCall_type("DELETE");
            String groupResult = client.performRequest(data);
            logger.debug("结果：" + groupResult);
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
            e.printStackTrace();
        }
    }

    /**
     * 转移所有人
     *
     * @param client
     * @param xObjectApiKey 对象名称
     * @param recordId      对象-数据id
     * @param targetUserId  目标用户
     */
    public static String v2TranferEntityXobject(RkhdHttpClient client, String xObjectApiKey, Long recordId, Long targetUserId) throws Exception {
        if (client == null) {
            client = RkhdHttpClient.instance();
        }
        RkhdHttpData data = new RkhdHttpData();
        data.setCallString("/rest/data/v2.0/xobjects/" + xObjectApiKey + "/actions/transfers?recordId=" + recordId + "&targetUserId=" + targetUserId);
        data.setCall_type("POST");
        setAdminPrivileges(true,data);
        return client.execute(data, ResponseBodyHandlers.ofString());
    }

    /***
     * 新版新增团队成员
     *
     * @param rkhdHttpClient
     * @param teamMember  团队成员相关参数
     */
    public static Boolean addTeamMember(RkhdHttpClient rkhdHttpClient, JSONObject teamMember) {
        JSONObject param = new JSONObject();
        boolean result = false;
        String url = "/rest/data/v2.0/xobjects/teamMember";

        param.put("userId", teamMember.getLong("userId"));
        // ownerFlag 1、有编辑权限 2、无编辑权限
        param.put("ownerFlag", teamMember.getInteger("ownerFlag"));
        param.put("recordFrom_data", teamMember.getLong("recordFrom_data"));
        param.put("recordFrom", teamMember.getLong("recordFrom"));

        JSONObject outObject = new JSONObject();
        logger.info("添加团队数据：" + param);
        outObject.put("data", param);

        RkhdHttpData data = RkhdHttpData.newBuilder().callType("POST").callString(url).body(outObject.toJSONString())
                .build();
        try {
            JSONObject resultObject = rkhdHttpClient.execute(data, ResponseBodyHandlers.ofJSON());

            logger.info("====>" + resultObject);
            if (resultObject.getLong("code") == 200) {
                logger.info("新增团队成员成功");
                result = true;
            } else {
                logger.info("新增团队成员失败" + resultObject);
            }

        } catch (Exception e) {
            logger.error("addContacts IOException" + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 获取通用选项集
     *
     * @param globalPickApiKey
     * @param client
     * @return <key,label>   <枚举值，显示值>
     */
    public static Map<Integer, String> getGlobalPicks(String globalPickApiKey, RkhdHttpClient client) {
        Map<Integer, String> map = new HashMap<Integer, String>();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString("/rest/metadata/v2.0/settings/globalPicks/" + globalPickApiKey);
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());

            if (StringUtils.isNotBlank(responseStr)) {
                logger.info("查询列表结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (responseCode.equals(0)) {
                    String resultStr = responseObject.getString("data");
                    JSONObject dataJson = JSONObject.parseObject(resultStr);
                    String records = dataJson.getString("records");
                    JSONArray pickOptions = JSONObject.parseObject(records).getJSONArray("pickOption");
                    for (int i = 0; i < pickOptions.size(); i++) {
                        JSONObject pickOption = pickOptions.getJSONObject(i);
                        map.put(pickOption.getInteger("optionCode"), pickOption.getString("optionLabel"));
                    }
                }
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return map;
    }

    /**
     * 获取通用选项集
     *
     * @param globalPickApiKey
     * @param client
     * @return <key,label>   <枚举值，显示值>
     */
    public static String getGlobalPicks(String globalPickApiKey, RkhdHttpClient client,Map<String, Integer> map) {
        String label = "";
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString("/rest/metadata/v2.0/settings/globalPicks/" + globalPickApiKey);
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());

            if (StringUtils.isNotBlank(responseStr)) {
                logger.info("查询列表结果：" + responseStr);
                JSONObject responseObject = JSONObject.parseObject(responseStr);
                Integer responseCode = responseObject.getIntValue("code");
                if (responseCode.equals(0)) {
                    String resultStr = responseObject.getString("data");
                    JSONObject dataJson = JSONObject.parseObject(resultStr);

                    JSONObject records = dataJson.getJSONObject("records");
                    label = records.getString("label");
                    JSONArray pickOptions = records.getJSONArray("pickOption");
                    for (int i = 0; i < pickOptions.size(); i++) {
                        JSONObject pickOption = pickOptions.getJSONObject(i);
                        map.put(pickOption.getString("optionLabel"),pickOption.getInteger("optionCode") );
                    }
                }
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return label;
    }
    /**
     * 更新通用选项集
     * @param client
     * @param globalPickApiKey
     * @param request
     */
    public static JSONObject updateGlobalPicks(RkhdHttpClient client, String globalPickApiKey, JSONObject request) {
        JSONObject result = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("PATCH");
            data.setCallString("/rest/metadata/v2.0/settings/globalPicks/" + globalPickApiKey);
            data.setBody(request.toJSONString());

            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            result = JSONObject.parseObject(responseStr);
            if(result.getString("code").equals("0")) {
                logger.info("更新成功");
            } else {
                logger.info("更新失败，错误原因："+result.getString("msg"));
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return result;
    }

    /**
     * 获取单个通用选项集依赖关系
     * @param client
     * @param controlApiKey
     * @param dependentItemApiKey
     *
     */
    public static JSONArray getGlobalPicksDependent(RkhdHttpClient client, String controlApiKey, String dependentItemApiKey) {
        JSONArray itemDependencyDetailAOs = new JSONArray();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString("/rest/metadata/v2.0/settings/globalPicks/dependencies/label/" + controlApiKey + "/" + dependentItemApiKey);

            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            JSONObject result = JSONObject.parseObject(responseStr);
            logger.info("结果："+result);
            if(result.getString("code").equals("0")) {
                itemDependencyDetailAOs = result.getJSONObject("data").getJSONArray("records").getJSONObject(0).getJSONArray("itemDependencyDetailAOs");
            } else {
                logger.info("获取失败，错误原因："+result.getString("msg"));
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return itemDependencyDetailAOs;
    }

    /**
     * 更新单个通用选项集依赖关系
     * @param client
     * @param controlApiKey
     * @param dependentItemApiKey
     */
    public static JSONObject updateGlobalPicksDependent(RkhdHttpClient client, JSONArray itemDependencyDetailAOs, String controlApiKey, String dependentItemApiKey) {
        JSONObject result = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("PATCH");
            data.setCallString("/rest/metadata/v2.0/settings/globalPicks/dependencies/label/" + controlApiKey + "/" + dependentItemApiKey);
            JSONObject request = new JSONObject();
            request.put("itemDependencyDetailAOs",itemDependencyDetailAOs);
            data.setBody(request.toJSONString());

            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            result = JSONObject.parseObject(responseStr);
            logger.info("结果："+result);
            if(result.getString("code").equals("0")) {
                itemDependencyDetailAOs = result.getJSONObject("data").getJSONArray("records").getJSONObject(0).getJSONArray("itemDependencyDetailAOs");
            } else {
                logger.info("获取失败，错误原因："+result.getString("msg"));
            }
        } catch (Exception e) {
            logger.error(" 报错信息：" + e);
        }
        return result;
    }
    /**
     * 获取商机阶段相关信息（阶段id，阶段状态，阶段名称）
     * 根据商机业务类型查询商机阶段
     */
    public static Map<Long, JSONObject> getStageListByEntityTypeApiKey(RkhdHttpClient client, String apiKey) throws ScriptBusinessException {
        Map<Long, JSONObject> map = new HashMap<Long, JSONObject>();
        try {
            JSONObject entityTypeApiKey = new JSONObject();
            entityTypeApiKey.put("entityTypeApiKey", apiKey);
            JSONObject parameter = new JSONObject();
            parameter.put("data", entityTypeApiKey);
            logger.info("请求参数：" + parameter.toJSONString());
            RkhdHttpData data = RkhdHttpData.newBuilder()
                    .callType("POST")
                    .callString("/rest/data/v2.0/xobjects/stage/actions/getStageListByEntityTypeApiKey")
                    .body(parameter.toJSONString())
                    .build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            logger.info("result结果:" + result);
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }

            JSONArray records = result.getJSONArray("data");
            for (int i = 0; i < records.size(); i++) {
                JSONObject json = records.getJSONObject(i);
                map.put(json.getLong("id"), json);
            }

        } catch (Exception e) {
            // TODO: handle exception
            logger.error(e.getMessage(), e);
            throw new ScriptBusinessException(e);
        }
        return map;
    }

    /**
     * 通用POST请求方法（适用自定义api和openapi）
     *
     * @param client
     * @param url
     * @param head
     * @param body
     * @throws Exception
     */
    public static JSONObject rkhdPost(RkhdHttpClient client, String url, JSONObject head, JSONObject body) throws Exception {
        logger.info("请求参数：" + body.toJSONString());
        RkhdHttpData data = new RkhdHttpData();
        data.setCallString(url);
        data.setCall_type("POST");
        data.setBody(body.toJSONString());
        if (head != null) {
            data.setHeaderMap((Map) head);
        }
        JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
        logger.info("result结果:" + result);
        if (200 != result.getInteger("code")) {
            throw new Exception(result.getString("msg"));
        }
        return result;
    }

    /**
     * POST请求
     * @param client
     * @param url
     * @param head
     * @param body
     * @return
     * @throws Exception
     */
    public static JSONObject rkhdPostApi(RkhdHttpClient client, String url, JSONObject head, JSONObject body) throws Exception {
        logger.info("请求参数：" + body.toJSONString());
        RkhdHttpData data = new RkhdHttpData();
        data.setCallString(url);
        data.setCall_type("POST");
        data.setBody(body.toJSONString());
        if (head != null) {
            data.setHeaderMap((Map) head);
        }
        JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
        logger.info("result结果:" + result);
        return result;
    }

    /**
     * POST请求
     * @param client
     * @param url
     * @param head
     * @param body
     * @return
     * @throws Exception
     */
    public static JSONObject rkhdDeleteApi(RkhdHttpClient client, String url, JSONObject head, JSONObject body) throws Exception {
        logger.info("请求参数：" + body.toJSONString());
        RkhdHttpData data = new RkhdHttpData();
        data.setCallString(url);
        data.setCall_type("DELETE");
        data.setBody(body.toJSONString());
        if (head != null) {
            data.setHeaderMap((Map) head);
        }
        JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
        logger.info("result结果:" + result);
        return result;
    }
    /**
     * 通用GET请求方法
     *
     * @param client
     * @param url
     * @param head
     * @throws Exception
     */
    public static JSONObject rkhdGet(RkhdHttpClient client, String url, JSONObject head) throws Exception {
        RkhdHttpData data = new RkhdHttpData();
        data.setCallString(url);
        data.setCall_type("GET");
        if (head != null) {
            data.setHeaderMap((Map) head);
        }
        JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
        logger.info("result结果:" + result);
        if (200 != result.getInteger("code") && 0 != result.getInteger("status")) {
            throw new Exception(result.getString("msg"));
        }
        return result;
    }
    /**
     * 通用GET请求方法
     *
     * @param client
     * @param url
     * @param head
     * @throws Exception
     */
    public static JSONObject rkhdGetApi(RkhdHttpClient client, String url, JSONObject head) throws Exception {
        RkhdHttpData data = new RkhdHttpData();
        data.setCallString(url);
        data.setCall_type("GET");
        if (head != null) {
            data.setHeaderMap((Map) head);
        }
        JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
        logger.info("result结果:" + result);

        return result;
    }
    /**
     * 新版审批相关处理--支持标准和自定义对象审批，商机阶段审批
     *
     * @param client
     * @param entityApiKey
     * @param action       "submit/agree/reject/..."
     * @param dataId       数据id
     * @param stageId      阶段id（阶段审批流时，必填）
     */
    public static void approvalAutoCommit(RkhdHttpClient client, String entityApiKey, String action, Long dataId, Long stageId) {
        try {
            JSONObject result = taskPreProcessor(client, entityApiKey, action, dataId, stageId);
            logger.debug("预处理结果:" + result);
            if (result.getString("code").equals("200")) {
                JSONObject actionResult = taskAction(client, entityApiKey, action, dataId, result.getJSONObject("data"), stageId);
                logger.debug("结果:" + actionResult);
            }
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error:" + e.getMessage());

        }
    }

    /**
     * 审批预处理
     *
     * @param client
     * @param entityApiKey
     * @param action
     * @param dataId
     * @param stageId      阶段id（阶段审批流时，必填）
     * @return
     */
    public static JSONObject taskPreProcessor(RkhdHttpClient client, String entityApiKey, String action, Long dataId, Long stageId) {
        JSONObject result = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            JSONObject body = new JSONObject();
            JSONObject record = new JSONObject();
            data.setCallString("/rest/data/v2.0/creekflow/task/actions/preProcessor");
            data.setCall_type("POST");
            record.put("entityApiKey", entityApiKey);
            record.put("action", action);
            record.put("dataId", dataId);
            if (stageId != null) {
                record.put("srcProcType", 4);
                record.put("srcProcInstId", -1);
                //阶段key 阶段id
                record.put("srcTaskDefKey", stageId);
            }
            body.put("data", record);
            data.setBody(body.toString());
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            logger.info("返回信息：" + responseStr);
            result = JSONObject.parseObject(responseStr);

        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error:" + e.getMessage());
        }
        return result;
    }

    /**
     * 审批操作
     *
     * @param client
     * @param entityApiKey
     * @param action
     * @param dataId
     * @param object       预处理接口返回的json消息
     * @param stageId      阶段id（阶段审批流时，必填）
     */
    public static JSONObject taskAction(RkhdHttpClient client, String entityApiKey, String action, Long dataId,
                                        JSONObject object, Long stageId) {
        JSONObject result = new JSONObject();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            JSONObject body = new JSONObject();
            JSONObject record = new JSONObject();
            data.setCallString("/rest/data/v2.0/creekflow/task");
            data.setCall_type("POST");
            record.put("entityApiKey", entityApiKey);
            record.put("action", action);
            record.put("dataId", dataId);
            record.put("procdefId", object.getLong("procdefId"));
            if (stageId != null) {
                record.put("srcProcType", 4);
                record.put("srcProcInstId", -1);
                //阶段key 阶段id
                record.put("srcTaskDefKey", stageId);
            }
            if (object.get("nextTaskDefKey") != null) {
                record.put("nextTaskDefKey", object.get("nextTaskDefKey"));
            }
            if (object.get("chooseApprover") != null) {
                JSONArray array = object.getJSONArray("chooseApprover");
                JSONArray nextAssignees = new JSONArray();
                if (array.size() == 0) {
                    logger.error("下一任务候选办理对象列表为空，请查看提交后节点的策略");
                }
                for (int i = 0; i < array.size(); i++) {
                    Long id = array.getJSONObject(i).getLong("id");
                    nextAssignees.add(id);
                }

                record.put("nextAssignees", nextAssignees);
            }
            logger.debug(record.toString());
            body.put("data", record);
            data.setBody(body.toString());
            String responseStr = client.performRequest(data);
            logger.info("返回信息：" + responseStr);
            result = JSONObject.parseObject(responseStr);

        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error:" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取当前数据的审批历史
     * @param client
     * @param entityApiKey   对象
     * @param dataId         数据id
     * @param stageFlg       是否阶段审批
     *
     */
    public static JSONArray queryApprovalHistory(RkhdHttpClient client,String entityApiKey,Long dataId,Boolean stageFlg) {
        JSONArray result = new JSONArray();
        String url = "/rest/data/v2.0/creekflow/history/filter?entityApiKey=%s&dataId=%s&stageFlg=%s";
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("GET");
            data.setCallString(String.format(url,entityApiKey,dataId,String.valueOf(stageFlg)));
            JSONObject resp = client.execute(data,ResponseBodyHandlers.ofJSON());
            if(RESULT_CODE.equals(resp.getIntValue("code"))) {
                result = resp.getJSONArray("data");
            }

        } catch (Exception e) {
            // TODO: handle exception
            logger.error("error:" + e.getMessage());
        }
        return result;
    }
    /**
     * xoql 查询接口
     *
     * @param client
     * @param sql
     * @return
     * @throws ScriptBusinessException
     * @throws IOException
     * @throws ApiEntityServiceException
     * @throws InterruptedException
     */
    public static JSONArray xoql(RkhdHttpClient client, String sql) throws ScriptBusinessException, XsyHttpException,InterruptedException {

        JSONArray records = new JSONArray();
        RkhdHttpData data = new RkhdHttpData();
        String url = "/rest/data/v2.0/query/xoql";
        logger.debug("url--->" + url);
        logger.debug("sql:" + sql);
        data.setCall_type("Post");
        data.setCallString(url);
        data.putFormData("xoql", sql);
        String result = client.execute(data,ResponseBodyHandlers.ofString());
        logger.debug(result);

        if (StringUtils.isNotBlank(result)) {
            JSONObject resultJson = JSONObject.parseObject(result);
            String code = resultJson.getString("code");

            if (code.equals("200")) {
                JSONObject resultobj = resultJson.getJSONObject("data");
                int count = resultobj.getIntValue("count");
                if (count > 0) {
                    records.addAll(resultobj.getJSONArray("records"));
                }
            } else if (code.equals("1020025")) {
                //因为返回信息为频率过高所以暂停一秒钟
                Thread.sleep(1000);
                records = xoql(client, sql);
            } else {
                throw new ScriptBusinessException(sql + "错误:-->" + code + "|错误原因:" + resultJson.getString("msg"));
            }
        } else {
            throw new ScriptBusinessException(sql + "错误:-->返回为空");
        }
        return records;
    }
    /**
     * 通知消息 跳转链接类型=关联对象
     *
     * @param client   RkhdHttpClient类型的连接器-null时自动生成
     * @param belongId 对象belong
     * @param dataId   数据id
     * @param userList 接收人列表
     * @param content  销售内容 例："您的客户{arg0}"
     */
    public static boolean notice(RkhdHttpClient client, long belongId, long dataId, List<Long> userList,
                                 String content) {
        logger.debug("通用方法,通知消息-开始");
        logger.debug("belongId:" + belongId + " dataId:" + dataId + " userList:" + userList.toString() + " content:"
                + content);
        boolean result = false;
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("Post");
            data.setCallString("/rest/notice/v2.0/newNotice");
            JSONObject noticeObject = new JSONObject();
            noticeObject.put("belongId", belongId);
            noticeObject.put("content", content);

            JSONArray mergeFields = new JSONArray();
            JSONObject mergeFieldsObject = new JSONObject();
            mergeFieldsObject.put("belongId", belongId);
            mergeFieldsObject.put("objectId", dataId);
            mergeFieldsObject.put("type", 1);
            mergeFields.add(mergeFieldsObject);
            noticeObject.put("mergeFields", mergeFields);
            // noticeObject.put("mergeFieldsIndex", 1);
            noticeObject.put("objectId", dataId);
            JSONArray receivers = new JSONArray();
            for (Long userId : userList) {
                JSONObject receiversObject = new JSONObject();
                receiversObject.put("id", userId);
                receiversObject.put("receiverType", 0);
                receivers.add(receiversObject);
            }
            noticeObject.put("receivers", receivers);
            logger.debug("请求参数：" + noticeObject.toString());
            data.setBody(noticeObject.toString());
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            logger.debug("返回信息：" + responseStr);
            JSONObject responseObject = JSONObject.parseObject(responseStr);
            Integer responseCode = responseObject.getIntValue("code");
            if (responseCode.equals(200)) {
                result = true;
            } else {
                logger.debug("返回信息：" + responseStr);
            }
        } catch (Exception e) {
            logger.error("通知消息报错，" + e.getMessage());
        }
        return result;
    }
    /**
     * 通知消息 跳转链接类型=内部链接 or 外部链接
     *
     * @param client   RkhdHttpClient类型的连接器-null时自动生成
     * @param belongId 对象belong
     * @param label   自定义超链显示的名称
     * @param userList 接收人列表
     * @param content  销售内容 例："您的客户{arg0}"
     * @param custLink 链接
     */
    public static boolean noticeCustLink(RkhdHttpClient client, long belongId, String label, List<Long> userList,
                                         String content,Integer type,String custLink) {
        logger.debug("通用方法,通知消息-开始");
        logger.debug("belongId:" + belongId + " label:" + label + " custLink:" + custLink + " userList:" + userList.toString() + " content:"
                + content);
        boolean result = false;
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            RkhdHttpData data = new RkhdHttpData();
            data.setCall_type("Post");
            data.setCallString("/rest/notice/v2.0/newNotice");
            JSONObject noticeObject = new JSONObject();
            noticeObject.put("content", content);

            JSONArray mergeFields = new JSONArray();
            JSONObject mergeFieldsObject = new JSONObject();
            mergeFieldsObject.put("label", label);
            mergeFieldsObject.put("customLink", belongId);

            mergeFieldsObject.put("type", type);
            mergeFields.add(mergeFieldsObject);
            noticeObject.put("mergeFields", mergeFields);

            JSONArray receivers = new JSONArray();
            for (Long userId : userList) {
                JSONObject receiversObject = new JSONObject();
                receiversObject.put("id", userId);
                //接收消息的类型(0：人，1：部门，2：群组)类型
                receiversObject.put("receiverType", 0);
                receivers.add(receiversObject);
            }
            noticeObject.put("receivers", receivers);
            logger.debug("请求参数：" + noticeObject.toString());
            data.setBody(noticeObject.toString());
            String responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            logger.debug("返回信息：" + responseStr);
            JSONObject responseObject = JSONObject.parseObject(responseStr);
            Integer responseCode = responseObject.getIntValue("code");
            if (responseCode.equals(200)) {
                result = true;
            } else {
                logger.debug("返回信息：" + responseStr);
            }
        } catch (Exception e) {
            logger.error("通知消息报错，" + e.getMessage());
        }
        return result;
    }



    public static String randomUUID(int length) {

        String result = "";
        for (int i = 0; i < length; i++) {

            int ran =(int) (Math.random() * 16);
            if (ran == 10) {
                result += "A";
            } else if (ran == 11) {
                result += "B";
            } else if (ran == 12) {
                result += "C";
            } else if (ran == 13) {
                result += "D";
            } else if (ran == 14) {
                result += "E";
            } else if (ran == 15) {
                result += "F";
            } else {
                result += "G";
            }

        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        NeoCrmRkhdService.rtnEnterpriseInfo(null, "浙江华资实业发展有限公司");
    }
}

