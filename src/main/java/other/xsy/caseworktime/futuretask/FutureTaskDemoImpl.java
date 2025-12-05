package other.xsy.caseworktime.futuretask;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.rkhd.platform.sdk.data.model.CaseMonTime__c;
import com.rkhd.platform.sdk.data.model.Case_WorkOrder__c;
import com.rkhd.platform.sdk.data.model.Event_Log__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.service.XoqlService;
import com.rkhd.platform.sdk.task.FutureTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author 武于伦
 * @version 1.0
 * @email 2717718875@qq.com
 * @data 2025/10/27 0:30
 */
public class FutureTaskDemoImpl implements FutureTask {

    private final Logger log = LoggerFactory.getLogger();

    @Override
    public void execute(String s) throws ScriptBusinessException {
            // s 转 jsonObject
        JSONObject jsonObject = JSON.parseObject(s);
        // 获取 saveList 进行插入
        List<CaseMonTime__c> saveList = JSON.parseArray(jsonObject.getJSONArray("saveList").toJSONString(), CaseMonTime__c.class);
        // 获取 jsonObject 中的saveList2 也就是map
        ArrayList<Case_WorkOrder__c> saveList2 = new ArrayList<>();
        Map<String, Case_WorkOrder__c> cHashMap = JSON.parseObject(jsonObject.getJSONObject("saveList2").toJSONString(), new TypeReference<Map<String, Case_WorkOrder__c>>() {});
        log.info(saveList.toString());
        log.info(cHashMap.toString());
        try {
            Long casemtid = MetadataService.instance().getBusiType("case_WorkOrder__c", "defaultBusiType").getId();
            // 记录日志方便查询
            Event_Log__c eventLog__c = new Event_Log__c();
            eventLog__c.setBody_Content__c(JSON.toJSONString(jsonObject));
            eventLog__c.setEntityType(MetadataService.instance().getBusiType("event_Log__c", "defaultBusiType").getId());
            BatchOperateResult insert = XObjectService.instance().insert(saveList, false, true);
            // 判断是否全部插入成功
            if (insert.getSuccess()){
                // 插入成功后匹配每条数据的id
                List<OperateResult> operateResults = insert.getOperateResults();
                for (int i = 0; i < operateResults.size(); i++) {
                    OperateResult result = operateResults.get(i);
                    CaseMonTime__c caseMonTime = saveList.get(i);
                    caseMonTime.setId(result.getDataId());
                   // 根据caseNumber找到对应的Case_WorkOrder__c，设置关联字段
                    String caseNumber = caseMonTime.getCaseNumber__c();
                    for (String key : cHashMap.keySet()) {
                        if (key.startsWith(caseNumber)) {
                            Case_WorkOrder__c workOrder = cHashMap.get(key);
                            workOrder.setCaseMonTime__c(result.getDataId());
                            workOrder.setEntityType(casemtid);
                            saveList2.add(workOrder);
                        }
                    }
                }
                eventLog__c.setResult__c(1);
            }else {
                eventLog__c.setResult__c(2);
                throw new RuntimeException(insert.getErrorMessage());
            }
            if (saveList2.size() > 0) {
                eventLog__c.setReturn_Result__c(JSON.toJSONString(saveList2));
                BatchOperateResult insert1 = XObjectService.instance().insert(saveList2, false, true);
                eventLog__c.setResult__c(1);
                if (!insert1.getSuccess()) {
                    eventLog__c.setResult__c(2);
                    throw new RuntimeException(insert1.getErrorMessage());
                }
            }
            XObjectService.instance().insert(eventLog__c);
        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }
    }
}
