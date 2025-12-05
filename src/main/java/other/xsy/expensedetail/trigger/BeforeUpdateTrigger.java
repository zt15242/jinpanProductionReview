package other.xsy.expensedetail.trigger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.ExpenseDetail__c;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.service.XoqlService;
import com.rkhd.platform.sdk.trigger.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 武于伦
 * @version 1.0
 * @date 2024/12/11 11:20
 * @e-mail 2717718875@qq.com
 * @description:
 */
public class BeforeUpdateTrigger implements Trigger {
    private static final Logger logger = LoggerFactory.getLogger();
    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {
        List<DataResult> result = new ArrayList<>();
        String msg ="";
        TriggerContext triggerContext = new TriggerContext();
        Boolean success = true;
        try {
            List<JSONObject> contextList = new ArrayList<>();
            List<XObject> xObjectList = triggerRequest.getDataList();
            String sql = "select id,amount__c,campaign__c from expenseDetail__c where id = "+xObjectList.get(0).getId();
            QueryResult<JSONObject> query = XoqlService.instance().query(sql, true, true);
            contextList.addAll(query.getRecords());
            triggerContext.set("oldList", JSON.toJSONString(contextList));
            for (XObject object : xObjectList) {
                ExpenseDetail__c temp = new ExpenseDetail__c();
                temp.setId(object.getId());
                temp.setEntityType(object.getAttribute("entityType"));
                result.add(new DataResult(true, "成功", temp));
            }
            logger.info("result==" + result);
            logger.info("contextList==" + JSON.toJSONString(contextList));
            logger.info("设置的上下文信息："+triggerContext);
            msg = "成功";
        }catch (Exception e){
            msg = "失败";
            success = false;
            throw new RuntimeException(e);
        }
        return new TriggerResponse(success, msg, result, triggerContext);
    }
}
