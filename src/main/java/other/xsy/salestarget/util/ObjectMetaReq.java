package other.xsy.salestarget.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.exception.XsyHttpException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import other.xsy.salestarget.pojo.PickOption;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ObjectMetaReq {


    private static final Integer RESULT_CODE = 200;


    public static ObjectMetaReq instance() throws IOException, XsyHttpException {
        return new ObjectMetaReq();
    }

    private Map<String, List<PickOption>> objectAllPicks;

    Map<String, String> objectApiKeyMap;

    Map<String, String> objectApiNameMap;

    public Map<String, String> objectColumnTypeMap;

    Map<String, Boolean> objectColumnIsUpdateMap;

    public ObjectMetaReq getObjectAllMeta(String objectApiKey) throws IOException, XsyHttpException {
        RkhdHttpClient client = RkhdHttpClient.instance();
        RkhdHttpData data = new RkhdHttpData();
        data.setCall_type("GET");
        data.setCallString("/rest/data/v2.0/xobjects/"+objectApiKey+"/description");
        return client.execute(data, (dataString) -> {
            JSONObject responseObject = JSON.parseObject(dataString);
            if (RESULT_CODE.equals(responseObject.getIntValue("code"))) {
                JSONObject responseObjectData = (JSONObject) responseObject.get("data");
                JSONArray fields = (JSONArray) responseObjectData.get("fields");
                objectAllPicks = new HashMap<>();
                objectApiKeyMap = new HashMap<>();
                objectApiNameMap = new HashMap<>();
                objectColumnTypeMap = new HashMap<>();
                objectColumnIsUpdateMap = new HashMap<>();
                for (Object item : fields) {
                    JSONObject itemJo = (JSONObject) item;
                    if (itemJo.get("type") != null) {
                        if("picklist".equals(itemJo.get("type"))) {
                            JSONArray selectitem = (JSONArray) itemJo.get("selectitem");
                            if(selectitem != null && selectitem.size() > 0) {
                                List<PickOption> pickOptionList = selectitem.stream().map(itemo -> {
                                    JSONObject itemojo = (JSONObject) itemo;
                                    PickOption pickOption = new PickOption();
                                    pickOption.setOptionCode(itemojo.getIntValue("value"));
                                    pickOption.setOptionLabel(itemojo.getString("label"));
                                    pickOption.setOptionApiKey(itemojo.getString("apiKey"));
                                    return pickOption;
                                }).collect(Collectors.toList());
                                objectAllPicks.put(itemJo.getString("apiKey"),pickOptionList);
                                objectApiNameMap.put(itemJo.getString("apiKey"),itemJo.getString("label"));
                            }
                        }
                        if("multipicklist".equals(itemJo.get("type"))) {
                            JSONArray checkitem = (JSONArray) itemJo.get("checkitem");
                            if(checkitem != null && checkitem.size() > 0) {
                                List<PickOption> pickOptionList = checkitem.stream().map(itemo -> {
                                    JSONObject itemojo = (JSONObject) itemo;
                                    PickOption pickOption = new PickOption();
                                    pickOption.setOptionCode(itemojo.getIntValue("value"));
                                    pickOption.setOptionLabel(itemojo.getString("label"));
                                    pickOption.setOptionApiKey(itemojo.getString("apiKey"));
                                    return pickOption;
                                }).collect(Collectors.toList());
                                objectAllPicks.put(itemJo.getString("apiKey"),pickOptionList);
                                objectApiNameMap.put(itemJo.getString("apiKey"),itemJo.getString("label"));
                            }
                        }
                        if("reference".equals(itemJo.get("type"))) {
                            objectApiKeyMap.put(itemJo.getString("apiKey"),itemJo.getJSONObject("referTo").getString("apiKey"));
                            objectApiNameMap.put(itemJo.getString("apiKey"),itemJo.getString("label"));
                        }
                        objectColumnTypeMap.put(itemJo.getString("apiKey"),String.valueOf(itemJo.get("type")));
                    }
                    objectColumnIsUpdateMap.put(itemJo.getString("apiKey"),itemJo.getBoolean("updateable"));
                }
                return this;
            } else {
                throw new RuntimeException("获取所有选项失败：objectApiKey:" + objectApiKey);
            }
        });
    }

    public Integer getOptionByApiKey(String objectPickApiKey, String optionApiKey) {
        List<PickOption> pickOptionList = objectAllPicks.get(objectPickApiKey);
        if (pickOptionList != null && pickOptionList.size() > 0) {
            PickOption pickOption = pickOptionList.stream().filter(objectPick -> objectPick.getOptionApiKey().equals(optionApiKey)).findAny().orElse(null);
            if(pickOption != null) {
                return pickOption.getOptionCode();
            }
        }
        throw new RuntimeException("选项无该optionLabel：objectPickApiKey：" + objectPickApiKey + ";optionLabel:" + optionApiKey);
    }

    public String getOptionByCode(String objectPickApiKey, Integer optionCode) {
        List<PickOption> pickOptionList = objectAllPicks.get(objectPickApiKey);
        if (pickOptionList != null && pickOptionList.size() > 0) {
            PickOption pickOption = pickOptionList.stream().filter(objectPick -> objectPick.getOptionCode().equals(optionCode)).findAny().orElse(null);
            if(pickOption != null) {
                return pickOption.getOptionApiKey();
            }
        }
        throw new RuntimeException("选项无该optionCode：objectPickApiKey：" + objectPickApiKey + ";optionCode:" + optionCode);
    }

    public String getOptionByLabel(String objectPickApiKey, String label) {
        List<PickOption> pickOptionList = objectAllPicks.get(objectPickApiKey);
        if (pickOptionList != null && pickOptionList.size() > 0) {
            PickOption pickOption = pickOptionList.stream().filter(objectPick -> objectPick.getOptionLabel().equals(label)).findAny().orElse(null);
            if(pickOption != null) {
                return pickOption.getOptionApiKey();
            }
        }
        throw new RuntimeException("选项无该optionLabel：objectPickApiKey：" + objectPickApiKey + ";optionLabel:" + label);
    }


    public String getReferenceApiKey(String apiKey) {
        String referapiKey = objectApiKeyMap.get(apiKey);
        if(referapiKey == null) {
            throw new RuntimeException("没有连接该对象：apiKey：" + apiKey);
        }
        return referapiKey;
    }

    public String getObjectLabel(String apiKey) {
        String labelName = objectApiNameMap.get(apiKey);
        if(labelName == null) {
            throw new RuntimeException("没有连接该对象：labelName：" + labelName);
        }
        return labelName;
    }

    public String getColumnTypeByApiKey(String apiKey) {
        String columnType = objectColumnTypeMap.get(apiKey);
        if(columnType == null) {
            throw new RuntimeException("没有连接该对象：apiKey：" + apiKey);
        }
        return columnType;
    }

    public String getApiKeyByColumnType(String columnType) {
        if(objectColumnTypeMap == null){
            throw new RuntimeException("没有连接该对象：columnType：" + columnType);
        }
        String apiKey = null;
        for (Map.Entry<String, String> entry : objectColumnTypeMap.entrySet()) {
            if(columnType.equals(entry.getValue())) {
                apiKey = entry.getKey();
            }
        }
        return apiKey;
    }

    public Boolean getIsUpdateByApiKey(String apiKey) {
        Boolean isUpdate = objectColumnIsUpdateMap.get(apiKey);
        if(isUpdate == null) {
            throw new RuntimeException("没有连接该对象：apiKey：" + apiKey);
        }
        return isUpdate;
    }

    /**
     * 获取所有选项
     * @return
     */
    public Map<String, List<PickOption>> getAllPicks() {
        return objectAllPicks;
    }
}
