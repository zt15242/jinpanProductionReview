package other.coloplast.impl.service.opportunity;

//商机通用方法

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Opportunity;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.exception.XsyHttpException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import com.rkhd.platform.sdk.http.handler.ResponseBodyHandlers;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class OpportunityService {
    private static final Logger logger = LoggerFactory.getLogger();

    public static Map<Long, String> queryOppStages(RkhdHttpClient client, HashSet<Long> oppIds){
        HashMap<Long,String> map = new HashMap<>();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            //调用销售易接口获取商机阶段
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/opportunity/stages?ids" + StringUtils.join(oppIds, ",")).build();
            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            if (200 != result.getInteger("code")) {
                throw new ScriptBusinessException(result.getString("msg"));
            }
            for (Object obj : result.getJSONObject("data").getJSONArray("records")) {
                JSONObject jsonObject = (JSONObject) obj;
                map.put(jsonObject.getLong("id"),jsonObject.getString("stageName"));
            }
        }catch (Exception e){
            logger.error("error->" + e.toString());
        }
        return map;
    }

    //根据商机业务类型查询商机阶段
    public static Map<String, Long> getOppStageMap(RkhdHttpClient client, String enpityType){

        Map<String, Long> oppstageMap = new HashMap<>();
        try {
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            //调用销售易接口获取商机阶段
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/stage/actions/getStageListByEntityTypeApiKey").build();
            data.setCall_type("POST");

            JSONObject entityTypeApiKey = new JSONObject();
            entityTypeApiKey.put("entityTypeApiKey", enpityType);
            JSONObject parameter = new JSONObject();
            parameter.put("data", entityTypeApiKey);
            data.setBody(parameter.toJSONString());
            logger.info("请求参数---" + parameter);
            String responseStr = client.performRequest(data);
            logger.info("返回信息：" + responseStr);
            JSONObject responseObject = JSONObject.parseObject(responseStr);
            Integer responseCode = responseObject.getIntValue("code");
            if (responseCode == 200) {
                JSONArray result = responseObject.getJSONArray("data");
                for(int i=0; i<result.size(); i++){
                    JSONObject obj = (JSONObject) result.get(i);
                    oppstageMap.put(obj.getString("stageName"),obj.getLong("id"));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return oppstageMap;
    }

    //更新销售机会
    public static Boolean updateOpp(RkhdHttpClient client, Long oppId, JSONObject opp){
        try{
            if (client == null) {
                client = RkhdHttpClient.instance();
            }
            //调用销售易接口获取商机阶段
            RkhdHttpData data = RkhdHttpData.newBuilder().callType("GET")
                    .callString("/rest/data/v2.0/xobjects/opportunity/" + oppId).build();
            data.setCall_type("PATCH");
            JSONObject record = new JSONObject();
            record.put("data", opp);
            data.setBody(record.toString());

            JSONObject result = client.execute(data, ResponseBodyHandlers.ofJSON());
            logger.info("返回信息：" + result);
            if (200 != result.getInteger("code")) {
                logger.error(result.getString("msg"));
                throw new ScriptBusinessException(result.getString("msg"));
            }
            return true;
        } catch (IOException | XsyHttpException | ScriptBusinessException e) {
            throw new RuntimeException(e);
        }
    }
}
