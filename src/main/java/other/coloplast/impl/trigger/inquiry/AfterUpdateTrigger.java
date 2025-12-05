package other.coloplast.impl.trigger.inquiry;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Inquiry__c;
import com.rkhd.platform.sdk.data.model.Opportunity;
import com.rkhd.platform.sdk.data.model.User;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.exception.TriggerContextException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.trigger.*;
import org.apache.commons.lang.StringUtils;
import other.coloplast.impl.common.*;
import other.coloplast.impl.service.inquiry.InquiryService;

import java.io.IOException;
import java.util.*;

public class AfterUpdateTrigger implements Trigger {
    private static final Logger logger = LoggerFactory.getLogger();
    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {

        TriggerContext triggerContext = triggerRequest.getTriggerContext();
        JSONArray jsonArray = null;

        List<DataResult> result = new ArrayList<>();
        Set<Long> userIds=new HashSet<>();
        Set<Long> oppIds=new HashSet<>();
        List<String> idS=new ArrayList<>();
        Set<Long> accIdS=new HashSet<>();
        Map<Long, User> userMap = new HashMap();
        Map<Long,Opportunity> oppMap = new HashMap();
        Map<Long, Integer> accoutnIndustryMap = new HashMap();
        Map<String,Long> officeMap= Utilities_General.getDepartMentManagerMap("0");
        Map<String,Long> departMap=Utilities_General.getDepartMentManagerMap("1");
        Map<Long, Inquiry__c> newMap= new HashMap<>();
        Map<Long, Inquiry__c> oldMap= new HashMap<>();
        List<XObject> needupdateApprovl = new ArrayList<>();

        List<Inquiry__c> needUpdateIny = new ArrayList<>();
        Set<Long> accId1s = new HashSet<>();

        List<Inquiry__c> updateList = new ArrayList<>();
        try {
            RkhdHttpClient client = RkhdHttpClient.instance();
            jsonArray = JSONArray.parseArray(triggerContext.get("oldList"));
            List<Inquiry__c> oldList = jsonArray.toJavaList(Inquiry__c.class);
            for(Inquiry__c old : oldList){
                oldMap.put(old.getId(),old);
            }

            List<XObject> dataList = triggerRequest.getDataList();
            for(XObject inq: dataList){
                newMap.put(inq.getId(), (Inquiry__c) inq);
                if(inq.getAttribute("approvelStatus__c").equals(1) || inq.getAttribute("approvelStatus__c").equals(3)){
                    needupdateApprovl.add(inq);
                }
            }

            for(Long id : newMap.keySet()){
                userIds.add(newMap.get(id).getOwnerId());
                oppIds.add(newMap.get(id).getOpportunity__c());
                //同步标识勾选。
                if(oldMap.containsKey(id) && oldMap.get(id).getCheck_CallOut__c() != oldMap.get(id).getCheck_CallOut__c() && oldMap.get(id).getCheck_CallOut__c()){
                    idS.add(String.valueOf(newMap.get(id).getId()));
                    accIdS.add(newMap.get(id).getAccount__c());
                }

                if(oldMap.containsKey(id) && oldMap.get(id).getAccount__c()!=newMap.get(id).getAccount__c()){
                    needUpdateIny.add(newMap.get(id));
                    accId1s.add(newMap.get(id).getAccount__c());
                }

            }

            if(!userIds.isEmpty()){
                String sql1 = "SELECT Id,Sales_Office__c FROM User WHERE Id IN (" + StringUtils.join(userIds,",") +")";
                QueryResult<XObject> userResult = XObjectService.instance().query(sql1, true, true);
                if(userResult.getSuccess()){
                    for(XObject user : userResult.getRecords()){
                        userMap.put(user.getId(), (User) user);
                    }
                }
            }
            logger.info("oppIds====" + JSONObject.toJSONString(oppIds));
            if(!oppIds.isEmpty()){
                List<Long> accId = new ArrayList<>();
                String oppsql = "SELECT Id, accountId, industry__c,Opportunity_Industry__c,OwnerId FROM Opportunity WHERE Id IN (" + StringUtils.join(oppIds,",") +")";
                QueryResult<XObject> oppResult = XObjectService.instance().query(oppsql, true, true);

                if(oppResult.getSuccess()){
                    for(XObject opp : oppResult.getRecords()){
                        oppMap.put(opp.getId(), (Opportunity) opp);
                        accId.add(((Opportunity) opp).getAccountId());
                        //accoutnIndustryMap.put(opp.getId(), opp.getAttribute("account.Accoutn_Industry__c"));
                    }
                }

                String accsql = "SELECT id, accoutn_Industry__c FROM account WHERE id IN ("+ StringUtils.join(accId,",")  +")";
                logger.info("accsql==="+accsql);
                QueryResult<XObject> accResult = XObjectService.instance().query(accsql, true, true);
                if(accResult.getSuccess()){
                    for(XObject acc : accResult.getRecords()){
                        accoutnIndustryMap.put(acc.getId(), acc.getAttribute("accoutn_Industry__c"));
                    }
                }
            }
            logger.info("oppMap====" + JSONObject.toJSONString(oppMap));
            logger.info("accoutnIndustryMap====" + JSONObject.toJSONString(accoutnIndustryMap));
            for(Long id : newMap.keySet()){
                Inquiry__c update = new Inquiry__c();
                update.setId(id);
                logger.info("newMap====" + JSONObject.toJSONString(newMap.get(id)));
                //取项目上的
                if(newMap.get(id).getAccount_Industry__c()==null){
                    logger.info("accoutnIndustryMap.get(oppMap.get(newMap.get(id).getOpportunity__c()).getAccountId())====" + accoutnIndustryMap.get(oppMap.get(newMap.get(id).getOpportunity__c()).getAccountId()));
                    update.setAccount_Industry__c(accoutnIndustryMap.get(oppMap.get(newMap.get(id).getOpportunity__c()).getAccountId()));
                }

                if(newMap.get(id).getOpportunity_Industry__c()==null && oppMap.get(newMap.get(id).getOpportunity__c())!=null){
                    update.setOpportunity_Industry__c(oppMap.get(newMap.get(id).getOpportunity__c()).getOpportunity_Industry__c());
                }

                logger.info("officeMap===" + JSONObject.toJSONString(officeMap));
                //代表处是否发生变更
                if(oldMap.containsKey(id) && oldMap.get(id).getSales_Office__c()!=newMap.get(id).getSales_Office__c()){
                    update.setSales_Manager__c(officeMap.get(newMap.get(id).getSales_Office__c()));

                    if (newMap.get(id).getAgency__c()==null) {
                        Long y = officeMap.get(newMap.get(id).getAgency__c());
                        if (StringUtils.isNotBlank(String.valueOf(y))) {
                            update.setSales_Manager__c(y);
                        }
                    }
                }
                logger.info("departMap===" + JSONObject.toJSONString(departMap));

                Map<String, Map<String, Integer>> kvMap = new HashMap<>();
                Map<String, Map<Integer, String>> vkMap = new HashMap<>();

                NeoCrmRkhdService.getPicklistValue(client, "inquiry__c", "label", kvMap, vkMap);
                Map<Integer, String> departmentMap = vkMap.get("department__c");

                logger.info("departmentMap===" + JSONObject.toJSONString(departmentMap));

                //事业部是否发生变更，
                if(oldMap.containsKey(id) && oldMap.get(id).getDepartment__c() != newMap.get(id).getDepartment__c()){
                    logger.info("newMap.get(id).getDepartment__c()===" + newMap.get(id).getDepartment__c());
                    update.setDepartMent_Manager__c(departMap.get(newMap.get(id).getDepartment__c().toString()));
                }
                result.add(new DataResult(true, null, newMap.get(id)));
                updateList.add(update);
            }

            //询价单标识勾选后，同步至智能平台
            if(!idS.isEmpty()){
                System.out.println("\n=== 测试接口请求 ===");
                //Utilities_Interface.outboundVporalExecute(idS,'Inquiry');
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
                    updateInq.setId(Long.valueOf(idS.get(0)));
                    updateInq.setIMS_Code__c(imsCode);
                    OperateResult uresult = XObjectService.instance().update(updateInq,true);
                    logger.info("updateInq" + JSONObject.toJSONString(updateInq));
                    if(uresult.getSuccess()){
                        logger.info("该询价单已经同步！");
                    }else{
                        logger.error(uresult.getErrorMessage());
                    }
                }else{
                    logger.error(JSONObject.toJSONString(json));
                }

            }
            if(!accIdS.isEmpty()){
                List<String> accIdStrings = new ArrayList<>();
                for(Long accId : accIdS){
                    accIdStrings.add(String.valueOf(accId));
                }
                CommoninterfaceUtil.sendInterfaceRequest( "Account", accIdStrings,"outbound");//Inquiry /SupplierToSAP
                //Utilities_Interface.outboundVporalExecute(accIdS,'Account');
            }

            //进程生成器 挪到 触发器 start
            if(!needUpdateIny.isEmpty()){
                InquiryService.updateaccNameSearch(needUpdateIny, accId1s);
            }
            //进程生成器 挪到 触发器 end

            logger.info("updateList===" + JSONObject.toJSONString(updateList));
            if(!updateList.isEmpty()){
                BatchOperateResult batchResult = XObjectService.instance().update(updateList,true,true);
                List<OperateResult> results = batchResult.getOperateResults();
                if (batchResult.getSuccess()) {
                    for (OperateResult oresult : results) {
                        logger.info("ID: " + oresult.getDataId());
                    }
                } else {
                    for (OperateResult oresult : results) {
                        logger.info("批量更新询价单失败: " + oresult.getErrorMessage());
                    }
                }
            }

            logger.info("needupdateApprovl==" + needupdateApprovl);
            //更新 合同专员和标书专员 20250928
            if(!needupdateApprovl.isEmpty()){
                ApproveResposibleUtil approveResposibleUtil = ApproveResposibleUtil.instance();
                approveResposibleUtil.approveResposible(dataList, "department__c", "sales_Office__c", "product_Group__c");
            }
        } catch (TriggerContextException | ApiEntityServiceException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return new TriggerResponse(true, "成功", result);
    }
}
