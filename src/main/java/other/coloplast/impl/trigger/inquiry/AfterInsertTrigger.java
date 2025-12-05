package other.coloplast.impl.trigger.inquiry;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Inquiry__c;
import com.rkhd.platform.sdk.data.model.Opportunity;
import com.rkhd.platform.sdk.data.model.ProType_Map__c;
import com.rkhd.platform.sdk.data.model.User;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.trigger.DataResult;
import com.rkhd.platform.sdk.trigger.Trigger;
import com.rkhd.platform.sdk.trigger.TriggerRequest;
import com.rkhd.platform.sdk.trigger.TriggerResponse;

import org.apache.commons.lang.StringUtils;
import other.coloplast.impl.common.ApproveResposibleUtil;
import other.coloplast.impl.common.NeoCrmRkhdService;
import other.coloplast.impl.common.Utilities_General;
import other.coloplast.impl.service.inquiry.InquiryService;
import other.coloplast.impl.service.opportunity.OpportunityService;

import java.util.*;

public class AfterInsertTrigger implements Trigger {

    private static Logger logger = LoggerFactory.getLogger();

    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {
        try {
            List<XObject> dataList = triggerRequest.getDataList();
            List<DataResult> result = new ArrayList<>();
            Set<Long> userIds=new HashSet<>();      //询价单所有人Set
            Set<Long> oppIds=new HashSet<>();       //项目Set
            Map<Long, Opportunity> oppMap = new HashMap();
            Map<Long, User> userMap = new HashMap();
            Map<Long, String> accoutnIndustryMap = new HashMap();
            List<Inquiry__c> updateList = new ArrayList<>();

            HashSet<Long> oppIdSet = new HashSet<>();

            List<JSONObject> updateJson = new ArrayList<>();

            List<Inquiry__c> needUpdateIny = new ArrayList<>();
            Set<Long> accIds = new HashSet<>();

            Map<String, Long> officeMap = Utilities_General.getDepartMentManagerMap("0");
            Map<String, Long> departMap = Utilities_General.getDepartMentManagerMap("1");

            RkhdHttpClient client = RkhdHttpClient.instance();

            for(XObject xObject : dataList){
                Inquiry__c inq = (Inquiry__c) xObject;
                userIds.add(inq.getOwnerId());
                oppIds.add(inq.getOpportunity__c());
                //新建询价单修改项目状态
                if(StringUtils.isNotBlank(Arrays.toString(inq.getQuoteType__c()))){
                    //正式报价
                    if(Arrays.asList(inq.getQuoteType__c()).contains(2)){
                        oppIdSet.add(inq.getOpportunity__c());
                    }
                }
                if(inq.getAccount__c()!=null){
                    needUpdateIny.add(inq);
                    accIds.add(inq.getAccount__c());
                }

                result.add(new DataResult(true, "成功", xObject));
            }

            if(!userIds.isEmpty()){
                String usersql = " SELECT Id,Sales_Office__c FROM User WHERE Id IN (" + StringUtils.join(userIds,",") + ")";
                QueryResult<User> userResult = XObjectService.instance().query(usersql, true, true);
                if(userResult.getSuccess()){
                    for(User user : userResult.getRecords()){
                        userMap.put(user.getId(), user);
                    }
                }
            }

            if(!oppIds.isEmpty()){
                String oppsql = "SELECT id,accountId.accoutn_Industry__c, industry__c,opportunity_Industry__c,ownerId,sales_Office__c, salesOfficeManger__c,agency__c FROM Opportunity WHERE id IN (" + StringUtils.join(oppIds,",") +")";
                String oppsql2 = " SELECT Id,Opportunity_Industry__c,OwnerId,Sales_Office__c, SalesOfficeManger__c,Agency__c FROM Opportunity WHERE Id IN (" + StringUtils.join(oppIds,",") +")";
                logger.info("oppsql====" + oppsql);
                JSONArray array = NeoCrmRkhdService.xoql(client, oppsql);
                QueryResult<XObject> oppResult = XObjectService.instance().query(oppsql2, true, true);
                logger.info("array===" + JSONObject.toJSONString(array));
                logger.info("oppResult===" + JSONObject.toJSONString(oppResult));
                if(!array.isEmpty()){
                    for(int i=0; i<array.size(); i++){
                        logger.info("opp====" + JSONObject.toJSONString(array.get(i)));
                        JSONObject opp = (JSONObject) array.get(i);
                        if(opp.getJSONArray("accountId.accoutn_Industry__c")!=null){
                            JSONArray accoutnIndustry = opp.getJSONArray("accountId.accoutn_Industry__c");
                            logger.info("accoutnIndustry====" + JSONObject.toJSONString(accoutnIndustry));
                            logger.info(" accoutnIndustry.get(0)====" + accoutnIndustry.get(0));
                            accoutnIndustryMap.put(opp.getLong("id"), (String) accoutnIndustry.get(0));
                        }

                    }
                    for(XObject opp : oppResult.getRecords()){
                        oppMap.put(opp.getId(), (Opportunity) opp);
                    }
                }
            }
            logger.info("accoutnIndustryMap=-==" + JSONObject.toJSONString(accoutnIndustryMap));
            logger.info("oppMap=-==" + JSONObject.toJSONString(oppMap));

            Map<String, Map<String, Integer>> kvMap = new HashMap<>();
            Map<String, Map<Integer, String>> vkMap = new HashMap<>();

            NeoCrmRkhdService.getPicklistValue(client, "account", "label", kvMap, vkMap);
            Map<String, Integer> accoutnIndustryIntegerMap = kvMap.get("accoutn_Industry__c");

            Map<String, Map<String, Integer>> kv1 = new HashMap<>();
            Map<String, Map<Integer, String>> vk2 = new HashMap<>();

            NeoCrmRkhdService.getPicklistValue(client, "inquiry__c", "label", kv1, vk2);

            Map<Integer, String> departmentMap = vk2.get("department__c");



            for(XObject xObject : dataList){
                Inquiry__c inq = (Inquiry__c) xObject;
                inq.setId(xObject.getId());
                logger.info("inq.getOpportunity__c()==" + inq.getOpportunity__c());
                if(oppMap.containsKey(inq.getOpportunity__c())){
                    logger.info("inq.getOpportunity__c()==进来了");
                    inq.setOwner__c(String.valueOf(oppMap.get(inq.getOpportunity__c()).getOwnerId()));

                    if(inq.getSales_Office__c() == null){
                        inq.setSales_Office__c(oppMap.get(inq.getOpportunity__c()).getSales_Office__c());
                        inq.setSales_Manager__c(oppMap.get(inq.getOpportunity__c()).getSalesOfficeManger__c());
                    }

                    if(inq.getAgency__c() == null){
                        inq.setAgency__c(oppMap.get(inq.getOpportunity__c()).getAgency__c());
                    }

                    if(inq.getAccount_Industry__c() == null && !accoutnIndustryMap.isEmpty()){
                        logger.info("accoutnIndustryMap.get(inq.getOpportunity__c())===" + accoutnIndustryMap.get(inq.getOpportunity__c()));
                        inq.setAccount_Industry__c( accoutnIndustryIntegerMap.get(accoutnIndustryMap.get(inq.getOpportunity__c())));
                    }

                    if(inq.getOpportunity_Industry__c() == null){
                        logger.info("oppMap.get(inq.getOpportunity__c()).getOpportunity_Industry__c()==" + oppMap.get(inq.getOpportunity__c()).getOpportunity_Industry__c());
                        inq.setOpportunity_Industry__c(oppMap.get(inq.getOpportunity__c()).getOpportunity_Industry__c());
                    }
                }

                //事业部不为空，获取对应的事业部总经理
                if(inq.getDepartment__c()!=null){
                    logger.info("departMap====" + JSONObject.toJSONString(departMap));
                    inq.setDepartMent_Manager__c(departMap.get(inq.getDepartment__c() + ""));
                }

                updateList.add(inq);
            }

            //进程生成器里面的逻辑 start
            if(!needUpdateIny.isEmpty()){
                InquiryService.updateaccNameSearch(needUpdateIny, accIds);
            }
            //进程生成器里面的逻辑 end

            //获取默认销售机会销售阶段的map
            Map<String, Long> oppStageMap = OpportunityService.getOppStageMap(client, "defaultBusiType");
            logger.info("oppStageMap===" + JSONObject.toJSONString(oppStageMap));
            logger.info("oppIdSet" + JSONObject.toJSONString(oppIdSet));
            //InquiryOpportuntiyHandler中的逻辑 start
            if(!oppIdSet.isEmpty()){
                String sql = "SELECT Id,agency__c, area__c, sales_Office__c, OwnerId, salesOfficeManger__c, stageName__c FROM Opportunity WHERE Id IN (" + StringUtils.join(oppIdSet,",") + ")";
                QueryResult<XObject> oppResult = XObjectService.instance().query(sql, true, true);
                if(oppResult.getSuccess()){
                    for(XObject object : oppResult.getRecords()){
                        Opportunity opp = (Opportunity) object;
                        logger.info("opp.getStageName__c()" + opp.getStageName__c());
                        if(!opp.getStageName__c().equals("报价/招投标") && !opp.getStageName__c().equals("签订合同") && !oppStageMap.isEmpty()){
                            Opportunity update = new Opportunity();
                            update.setId(opp.getId());
                            //这些询价需要更新
                            JSONObject temp = new JSONObject();
                            temp.put("id", opp.getId());
                            temp.put("saleStageId", oppStageMap.get("报价/招投标"));
                            updateJson.add(temp);
                        }
                    }
                }
                logger.info("updateJson==" + JSONObject.toJSONString(updateJson));
                if(!updateJson.isEmpty()){
                    for(JSONObject json : updateJson){
                        Long id = json.getLong("id");
                        Boolean updateresult = OpportunityService.updateOpp(client, id ,json);
                    }
                }
            }

            //更新
            logger.info("updateList====" + JSONObject.toJSONString(updateList));
            if(!updateList.isEmpty()){
                BatchOperateResult batchresult = XObjectService.instance().update(updateList,true,true);
                if(batchresult.getSuccess()){
                    for (OperateResult oresult : batchresult.getOperateResults()) {
                        logger.info("ID: " + oresult.getDataId());
                    }
                }else {
                    for (OperateResult oresult : batchresult.getOperateResults()) {
                        logger.info("批量更新询价单失败: " + oresult.getErrorMessage());
                    }
                }
            }
            //InquiryOpportuntiyHandler  end

            //更新 合同专员和标书专员 20250928
            ApproveResposibleUtil approveResposibleUtil = ApproveResposibleUtil.instance();
            approveResposibleUtil.approveResposible(dataList, "department__c", "sales_Office__c", "product_Group__c");

            return new TriggerResponse(true, "成功", result);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

}
