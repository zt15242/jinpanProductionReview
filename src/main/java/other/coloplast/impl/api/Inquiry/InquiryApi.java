package other.coloplast.impl.api.Inquiry;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.api.annotations.*;
import com.rkhd.platform.sdk.data.model.InquiryItem__c;
import com.rkhd.platform.sdk.data.model.Inquiry__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.param.ScheduleJobParam;
import com.rkhd.platform.sdk.service.XObjectService;
import org.apache.commons.lang.StringUtils;
import other.coloplast.impl.common.CommoninterfaceUtil;
import other.coloplast.impl.common.NeoCrmRkhdService;
import other.coloplast.impl.common.XMLParser;

import java.io.IOException;
import java.util.*;

@RestApi(baseUrl = "/inquiryApi")
public class InquiryApi {
    private Logger logger = LoggerFactory.getLogger();

    @RestMapping(value = "/getReasonNoBidMap", method = RequestMethod.GET)
    public Map<Integer, String> getReasonNoBidMap() {
        Map<String, Map<String, Integer>> kvMap = new HashMap<>();
        Map<String, Map<Integer, String>> vkMap = new HashMap<>();

        try {
            RkhdHttpClient client = RkhdHttpClient.instance();
            NeoCrmRkhdService.getPicklistValue(client, "inquiry__c", "label", kvMap, vkMap);

            return vkMap.get("reason_NoBid__c");
        } catch (ScriptBusinessException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    //获取询价单 的 销售总监补充意见
    @RestMapping(value = "/getAdvice", method = RequestMethod.GET)
    public JSONObject getAdvice(@RestQueryParam(name = "dataId") String dataId) {
        try {
            String sql = "SELECT id, advice__c FROM Inquiry__c WHERE id = " + dataId;
            logger.info(sql);
            QueryResult<Inquiry__c> inqs = XObjectService.instance().query(sql,true);
            JSONObject response = new JSONObject();
            if (!inqs.getSuccess()) {
                response.put("response", "error");
                response.put("errorMessage", inqs.getErrorMessage());
                logger.error(inqs.getErrorMessage());
            } else {
                response.put("response", "success");
                response.put("result", inqs.getRecords().get(0).getAdvice__c());
                logger.info("success==" + JSONObject.toJSONString(response));
            }
            return response;
        } catch (ApiEntityServiceException e) {
            JSONObject response = new JSONObject();
            response.put("response", "error");
            response.put("errorMessage", e.getMessage());
            logger.info("error==" + JSONObject.toJSONString(response));
            return response;
        }
    }

    //更新询价单的询价不参与询价原因
    @RestMapping(value = "/getReasonNoBidMap", method = RequestMethod.POST)
    public JSONObject updateIsReBid(@RestBeanParam(name = "body") Map<String, Object> params) {
        try {
            logger.info("params====" + JSONObject.toJSONString(params));
            String id = (String) params.get("id");
            String reasonDescription = (String) params.get("reasonDescription");
            //Integer[] reasonNoBid = (Integer[]) params.get("reasonNoBid");

            Integer[] integers = new Integer[0];
            logger.info("params.get(\"reasonNoBid\")===" + params.get("reasonNoBid"));
            if (params.get("reasonNoBid") instanceof List) {
                List<?> list = (List<?>) params.get("reasonNoBid");
                integers = list.toArray(new Integer[0]);
            }

            Inquiry__c updateInq = new Inquiry__c();
            updateInq.setId(Long.valueOf(id));
            updateInq.setIs_ReBid__c(1);
            updateInq.setReason_NoBid__c(integers);
            updateInq.setReasonDescription__c(reasonDescription);
            updateInq.setApprovelStatus__c(3);
            updateInq.setLockStatus(1);
            logger.info("updateInq====" + updateInq);
            OperateResult result = XObjectService.instance().update(updateInq,true);
            JSONObject response = new JSONObject();
            if (!result.getSuccess()) {
                response.put("response", "error");
                response.put("errorMessage", result.getErrorMessage());
                logger.info("error==" + JSONObject.toJSONString(response));
            } else {
                response.put("response", "success");
                logger.info("success==" + JSONObject.toJSONString(response));
            }
            return response;
        } catch (ApiEntityServiceException e) {
            JSONObject response = new JSONObject();
            response.put("response", "error");
            response.put("errorMessage", e.getMessage());
            logger.info("error==" + JSONObject.toJSONString(response));
            return response;
        }
    }

    //更新询价单的询价补充意见
    @RestMapping(value = "/updateAdvice", method = RequestMethod.POST)
    public JSONObject updateAdvice(@RestBeanParam(name = "body") Map<String, Object> params) {
        try {
            String id = (String) params.get("id");
            String advice = (String) params.get("advice");

            Inquiry__c updateInq = new Inquiry__c();
            updateInq.setId(Long.valueOf(id));
            updateInq.setAdvice__c(advice);

            OperateResult result = XObjectService.instance().update(updateInq,true);
            JSONObject response = new JSONObject();
            if (!result.getSuccess()) {
                response.put("response", "error");
                response.put("errorMessage", result.getErrorMessage());
                logger.info("error==" + JSONObject.toJSONString(response));
            } else {
                response.put("response", "success");
                logger.info("success==" + JSONObject.toJSONString(response));
            }
            return response;
        } catch (ApiEntityServiceException e) {
            JSONObject response = new JSONObject();
            response.put("response", "error");
            response.put("errorMessage", e.getMessage());
            logger.info("error==" + JSONObject.toJSONString(response));
            return response;
        }
    }

    //获取询价单关联的询价单产品信息
    @RestMapping(value = "/getInquiryItems", method = RequestMethod.GET)
    public JSONObject getInquiryItems(@RestQueryParam(name = "dataId") String dataId) {

        JSONObject response = new JSONObject();
        JSONArray data = new JSONArray();
        try {
            String sql = "SELECT id, productType__c, productName__c, proName__c, model__c, qty__c, remark__c FROM InquiryItem__c WHERE inquiry__c = " + dataId;
            logger.info(sql);
            QueryResult<InquiryItem__c> inqItems = XObjectService.instance().query(sql,true);

            if (inqItems.getSuccess()) {
                Map<String, Map<String, Integer>> kvMap = new HashMap<>();
                Map<String, Map<Integer, String>> vkMap = new HashMap<>();

                RkhdHttpClient client = RkhdHttpClient.instance();
                NeoCrmRkhdService.getPicklistValue(client, "inquiryItem__c", "label", kvMap, vkMap);

                Map<Integer, String> typeMap = vkMap.get("productType__c");
                Map<Integer, String> nameMap = vkMap.get("productName__c");

                for (InquiryItem__c item : inqItems.getRecords()) {
                    JSONObject iItem = new JSONObject();
                    iItem.put("id", item.getId());
                    iItem.put("category", typeMap.get(item.getProductType__c()));
                    iItem.put("name", nameMap.get(item.getProductName__c()));
                    iItem.put("inquiryName", item.getProName__c());
                    iItem.put("spec", item.getModel__c());
                    iItem.put("quantity", item.getQty__c());
                    iItem.put("remark", item.getRemark__c());
                    data.add(iItem);
                }
                response.put("response", "success");
                response.put("data", data);
            } else {
                response.put("response", "error");
                response.put("errorMessage", inqItems.getErrorMessage());
            }

        } catch (ApiEntityServiceException | ScriptBusinessException | IOException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    //获取产品类别选项
    @RestMapping(value = "/getProductTypeMap", method = RequestMethod.GET)
    public JSONObject getProductTypeMap() {
        JSONObject response = new JSONObject();

        Map<String, Map<String, Integer>> kvMap = new HashMap<>();
        Map<String, Map<Integer, String>> vkMap = new HashMap<>();

        try {
            RkhdHttpClient client = RkhdHttpClient.instance();
            NeoCrmRkhdService.getPicklistValue(client, "inquiryItem__c", "label", kvMap, vkMap);
            List<String> vkLIst =new ArrayList<>();
            for(Integer key : vkMap.get("productType__c").keySet()){
                vkLIst.add(vkMap.get("productType__c").get(key));
            }

            response.put("response", "success");
            response.put("data", vkLIst);
            return response;
        } catch (ScriptBusinessException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    //获取产品名称选项
    @RestMapping(value = "/getProductNameMap", method = RequestMethod.GET)
    public JSONObject getProductNameMap() {

        JSONObject response = new JSONObject();

        Map<String, Map<String, Integer>> kvMap = new HashMap<>();
        Map<String, Map<Integer, String>> vkMap = new HashMap<>();

        try {
            RkhdHttpClient client = RkhdHttpClient.instance();
            NeoCrmRkhdService.getPicklistValue(client, "inquiryItem__c", "label", kvMap, vkMap);
            List<String> vkLIst =new ArrayList<>();
            for(Integer key : vkMap.get("productName__c").keySet()){
                vkLIst.add(vkMap.get("productName__c").get(key));
            }

            response.put("response", "success");
            response.put("data", vkLIst);
            return response;
        } catch (ScriptBusinessException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    //保存询价单产品信息
    @RestMapping(value = "/saveInquiryItem", method = RequestMethod.POST)
    public JSONObject saveInquiryItem(@RestBeanParam(name = "body") Map<String, Object> params) throws IOException, ScriptBusinessException {

        JSONObject response = new JSONObject();
        List<InquiryItem__c> addList = new ArrayList<>();
        List<InquiryItem__c> updateList = new ArrayList<>();
        List<InquiryItem__c> deleteList = new ArrayList<>();
        logger.info("params==" + JSONObject.toJSONString(params));
        String id = (String) params.get("id");
        JSONArray addItems = (JSONArray) params.get("addItems");
        JSONArray deleteItems = (JSONArray) params.get("deleteItems");

        RkhdHttpClient client = RkhdHttpClient.instance();
        Long inquiryItemRntity = NeoCrmRkhdService.getEntityTypesId(client,"inquiryItem__c","defaultBusiType");

        //获取产品类别和产品名称的mapping关系
        Map<String, Map<String, Integer>> kvMap = new HashMap<>();
        Map<String, Map<Integer, String>> vkMap = new HashMap<>();
        NeoCrmRkhdService.getPicklistValue(client, "inquiryItem__c", "label", kvMap, vkMap);

        Map<String, Integer> productNameMap = kvMap.get("productName__c");
        Map<String, Integer> productTypeMap = kvMap.get("productType__c");


        for (int i = 0; i < addItems.size(); i++) {
            JSONObject object = (JSONObject) addItems.get(i);
            //前台生成一个小于1000的随机数，所以长度小于4就是说前台新增的
            if(object.getString("id").length()<=4){
                InquiryItem__c inquiryitem = new InquiryItem__c();
                //inquiryitem.setProductType__c(productTypeMap.get(object.getString("category")));
                //inquiryitem.setProductName__c(productNameMap.get(object.getString("name")));
                inquiryitem.setProductType__c(productTypeMap.get(object.getString("category")));
                inquiryitem.setProductName__c(productNameMap.get(object.getString("name")));
                inquiryitem.setProName__c(object.getString("inquiryName"));
                inquiryitem.setQty__c(object.getDouble("quantity"));
                inquiryitem.setRemark__c(object.getString("remark"));
                inquiryitem.setInquiry__c(Long.valueOf(id));
                inquiryitem.setEntityType(inquiryItemRntity);
                inquiryitem.setModel__c(object.getString("spec"));
                addList.add(inquiryitem);
            }else{
                InquiryItem__c inquiryitem1 = new InquiryItem__c();
                inquiryitem1.setId(Long.valueOf(object.getString("id")));
                inquiryitem1.setProductType__c(productTypeMap.get(object.getString("category")));
                inquiryitem1.setProductName__c(productNameMap.get(object.getString("name")));
//                inquiryitem1.setProductType__c(object.getInteger("category"));
//                inquiryitem1.setProductName__c(object.getInteger("name"));
                inquiryitem1.setProName__c(object.getString("inquiryName"));
                inquiryitem1.setQty__c(object.getDouble("quantity"));
                inquiryitem1.setRemark__c(object.getString("remark"));
                inquiryitem1.setInquiry__c(Long.valueOf(id));
                inquiryitem1.setEntityType(inquiryItemRntity);
                inquiryitem1.setModel__c(object.getString("spec"));
                updateList.add(inquiryitem1);
            }
        }

        if(!deleteItems.isEmpty()){
            for (int i = 0; i < deleteItems.size(); i++){
                JSONObject object = (JSONObject) addItems.get(i);
                InquiryItem__c temp = new InquiryItem__c();
                temp.setId(Long.valueOf(object.getString("id")));
                deleteList.add(temp);
            }
        }


        logger.info("addList===" + JSONObject.toJSONString(addList));
        try {
            if (!addList.isEmpty()) {
                BatchOperateResult batchResult = XObjectService.instance().insert(addList, true, true);
                List<OperateResult> results = batchResult.getOperateResults();
                if (batchResult.getSuccess()) {
                    for (OperateResult oresult : results) {
                        logger.info("ID: " + oresult.getDataId());
                    }
                    logger.info("deleteList===" + JSONObject.toJSONString(deleteList));
                    if (!deleteList.isEmpty()) {
                        BatchOperateResult deleteResult = XObjectService.instance().delete(deleteList, true);
                        if (deleteResult.getSuccess()) {
                            response.put("response", "success");
                        } else {
                            for (OperateResult oresult : results) {
                                logger.info("批量删除询价单产品信成功: " + deleteResult.getErrorMessage());
                            }

                            response.put("response", "error");
                            response.put("errorMessage", "删除失败");
                        }
                    }else{
                        response.put("response", "success");
                    }
                } else {
                    for (OperateResult oresult : results) {
                        logger.info("批量新建询价单产品信息失败: " + oresult.getErrorMessage());
                    }
                    response.put("response", "error");
                    response.put("errorMessage", "新建失败");
                }
            }
            logger.info("updateList===" + JSONObject.toJSONString(updateList));
            if(!updateList.isEmpty()){
                BatchOperateResult batchResult = XObjectService.instance().update(updateList, true, true);
                List<OperateResult> results = batchResult.getOperateResults();
                if (batchResult.getSuccess()) {
                    for (OperateResult oresult : results) {
                        logger.info("ID: " + oresult.getDataId());
                    }
                    response.put("response", "success");
                    response.put("message", "更新成功");
                }else{
                    for (OperateResult oresult : results) {
                        response.put("response", "error");
                        response.put("errorMessage", "更新失败");
                        logger.info("批量更新询价单产品信息失败: " + oresult.getErrorMessage());
                    }
                }
            }
        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }

        return response;
    }

    @RestMapping(value = "/sandIms", method = RequestMethod.GET)
    public JSONObject sandIms(@RestQueryParam(name = "dataId") String dataId) {
        JSONObject response = new JSONObject();

        String sql = "SELECT id, iMS_Code__c FROM inquiry__c WHERE id=" + dataId;
        try{
            QueryResult result  = XObjectService.instance().query(sql,true);
            if(result.getSuccess()){
                List<Inquiry__c> inqList = result.getRecords();
                Inquiry__c inq = inqList.get(0);
                if(inq.getIMS_Code__c()!=null){
                    response.put("code", 500);
                    response.put("message", "该询价单已经同步！");
                }else{
                    //调用接口
                    System.out.println("\n=== 测试接口请求 ===");
                    List<String> idS = new ArrayList<>();
                    idS.add(dataId);
                    String implResponse = CommoninterfaceUtil.sendInterfaceRequest( "Inquiry", idS,"outbound");//Inquiry /SupplierToSAP
                    logger.info("implResponse===" + implResponse);
                    implResponse = XMLParser.cleanXmlString(implResponse);
                    implResponse.replaceAll("\\\\\\\\r","");
                    implResponse.replaceAll("\\\\\\\\n","");
                    implResponse.replaceAll("\\\\\\\\t","");
                    implResponse = XMLParser.processXmlString(implResponse);
                    String s1 = XMLParser.xmlToJson(implResponse);  // 使用当前类的xmlToJson方法
                    JSONObject json = JSONObject.parseObject(s1);
                    //xml转JSONObject  IMScode赋值
                    logger.info("json===" + JSONObject.toJSONString(json));
                    logger.info("json===" + json.getJSONObject("response").getJSONObject("fields").getString("msg"));
                    if(json.getJSONObject("response").getJSONObject("fields").getString("msg").equals("操作成功")){

                        JSONObject responese = json.getJSONObject("response");
                        String imsCode = responese.getJSONObject("fields").getString("imsCode");
                        logger.info("imsCode===" + imsCode);
                        Inquiry__c updateInq = new Inquiry__c();
                        updateInq.setId(Long.valueOf(dataId));
                        updateInq.setIMS_Code__c(imsCode);
                        OperateResult uresult = XObjectService.instance().update(updateInq,true);
                        logger.info("updateInq" + JSONObject.toJSONString(updateInq));
                        if(uresult.getSuccess()){
                            response.put("code", 200);
                            response.put("message", "该询价单已经同步！");
                        }else{
                            logger.error(uresult.getErrorMessage());
                            response.put("code", 500);
                            response.put("message", uresult.getErrorMessage());
                        }
                    }else{
                        response.put("code", 500);
                        response.put("message", "同步过程出错！请联系管理员！");
                    }
                }
            }
        } catch (ApiEntityServiceException e) {
            response.put("code", 500);
            response.put("message", e.getMessage());
            throw new RuntimeException(e);
        }
        return response;
    }
}

