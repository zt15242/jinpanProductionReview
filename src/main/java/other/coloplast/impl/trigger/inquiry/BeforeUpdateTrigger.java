package other.coloplast.impl.trigger.inquiry;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Inquiry__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.exception.TriggerContextException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.trigger.*;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class BeforeUpdateTrigger implements Trigger {
    private static Logger logger = LoggerFactory.getLogger();
    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {

        TriggerContext triggerContext = new TriggerContext();
        List<XObject> xObjectList= triggerRequest.getDataList();
        List<Long> inquiryIds = new ArrayList<>();
        Map<Long, Inquiry__c> oldmap = new HashMap<>();
        Map<Long, Inquiry__c> newmap = new HashMap<>();

        List<Map<String, Object>> contextList = new ArrayList<>();

        List<DataResult> result = new ArrayList<>();

        try {
            for (XObject object : xObjectList) {
                inquiryIds.add(object.getId());
                newmap.put(object.getId(), (Inquiry__c) object);
            }
            List<XObject> oldInquiry = XObjectService.instance().getByIds("Inquiry__c", inquiryIds,true);

            for (XObject old : oldInquiry) {
                oldmap.put(old.getId(), (Inquiry__c) old);
            }

            for(Long id : newmap.keySet()){
                logger.info("oldmap.containsKey(id)" + oldmap.get(id).getCheck__c());
                logger.info("oldmap.containsKey(id)" + newmap.get(id).getCheck__c());
                logger.info("newmap.get(id).getIsApprove__c()" + newmap.get(id).getIsApprove__c());
                if(oldmap.containsKey(id) && oldmap.get(id).getCheck__c()!=newmap.get(id).getCheck__c() && newmap.get(id).getCheck__c() && (newmap.get(id).getIsApprove__c()==null|| newmap.get(id).getIsApprove__c().equals(2))){
                    result.add(new DataResult(false, "必须选择 是否需要总监/事业部总经理 审批，才可批准.", newmap.get(id)));
                }

                else{
                    Map<String, Object> pcMap = new HashMap<>();
                    pcMap.put("id", oldmap.get(id).getId());
                    pcMap.put("check_CallOut__c", oldmap.get(id).getAttribute("check_CallOut__c"));
                    pcMap.put("sales_Office__c", oldmap.get(id).getAttribute("sales_Office__c"));
                    pcMap.put("department__c", oldmap.get(id).getAttribute("department__c"));
                    pcMap.put("account__c", oldmap.get(id).getAttribute("account__c"));
                    contextList.add(pcMap);

                    result.add(new DataResult(true, null, newmap.get(id)));
                }
            }

            triggerContext.set("oldList", JSON.toJSONString(contextList));

            Boolean flagTotal = true;
            for(DataResult  re : result){
                if(!re.getSuccess()){
                    flagTotal = false;
                }
            }

            logger.info("flagTotal====" + flagTotal);
            logger.info("account====BeforeInsertTrigger====result" + JSONObject.toJSONString(result));
            if(flagTotal){
                return new TriggerResponse(flagTotal, "成功", result, triggerContext);
            }else{
                return new TriggerResponse(flagTotal, "失败", result);
            }

        } catch (ApiEntityServiceException | TriggerContextException e) {
            throw new RuntimeException(e);
        }
    }
}
