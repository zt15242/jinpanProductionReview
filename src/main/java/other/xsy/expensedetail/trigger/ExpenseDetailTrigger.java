package other.xsy.expensedetail.trigger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Campaign;
import com.rkhd.platform.sdk.data.model.ExpenseDetail__c;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.trigger.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 武于伦
 * @version 1.0
 * @email 2717718875@qq.com
 * @data 2025/9/15 12:42
 */
public class ExpenseDetailTrigger  implements Trigger {

    private static final Logger logger = LoggerFactory.getLogger();

    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {
        List<DataResult> result = new ArrayList<>();
        String msg ="";
        TriggerContext triggerContext = new TriggerContext();
        logger.info(JSON.toJSONString(triggerRequest));
        Boolean success = true;
        List<ExpenseDetail__c> oldList = new ArrayList<>();
        try {
            logger.info("费用报销明细触发器");
            List<XObject> xObjectList = triggerRequest.getDataList();
            String oldListStr = triggerRequest.getTriggerContext().get("oldList");
            logger.info(oldListStr);
            if (oldListStr != null) {
                oldList = JSONArray.parseArray(oldListStr, ExpenseDetail__c.class);
            }
            // 查询市场活动不为空的
            Set<Long> collect = xObjectList.stream()
                    .filter(expenseDetail -> ((ExpenseDetail__c) expenseDetail).getCampaign__c() != null)
                    .map(expenseDetail -> ((ExpenseDetail__c) expenseDetail).getCampaign__c())
                    .collect(Collectors.toSet());
            if (oldList.size()>0){
                for (ExpenseDetail__c expenseDetail__c : oldList) {
                    if (expenseDetail__c.getCampaign__c()!=null) {
                        collect.add(expenseDetail__c.getCampaign__c());
                    }
                }
            }
            logger.info("需要重新累计的市场活动："+ JSON.toJSONString(collect));
            if (collect.size()>0){
                String sql = "select id,amount__c,campaign__c from expenseDetail__c where campaign__c  in (" + parseListToStr2(collect)+")";
                logger.info("sql:"+sql);
                QueryResult<ExpenseDetail__c> query = XObjectService.instance().query(sql, true, true);
                HashMap<Long, Double> map = new HashMap<>();
                // 先给每个 市场活动的金额 初始为空
                for (Long l : collect) {
                    map.put(l,0.0);
                }
                // 给每个市场活动的金额 进行累计汇总
                for (ExpenseDetail__c record : query.getRecords()) {
                    if (map.containsKey(record.getCampaign__c())) {
                        map.put(record.getCampaign__c(), map.get(record.getCampaign__c()) + record.getAmount__c());
                    }else {
                        map.put(record.getCampaign__c(), record.getAmount__c());
                    }
                }
                logger.info("map:"+JSON.toJSONString(map));
                ArrayList<Campaign> campaignArrayList = new ArrayList<>();
                if (!map.isEmpty()){
                    for (Map.Entry<Long, Double> entry : map.entrySet()) {
                        Campaign campaign = new Campaign();
                        campaign.setId(entry.getKey());
                        campaign.setActualCost(entry.getValue());
                        campaignArrayList.add(campaign);
                        result.add(new DataResult(true,"",campaign));
                    }
                    logger.info("campaignArrayList:" + JSON.toJSONString(campaignArrayList));
                    BatchOperateResult update = XObjectService.instance().update(campaignArrayList,true,true);
                }
            }else {
                for (XObject xObject : xObjectList) {
                    result.add(new DataResult(true, "",xObject));
                }
            }
        }catch (Exception e) {
            success = false;
            msg = e.getMessage();
            logger.error(msg);
        }
        return new TriggerResponse(success, msg, result, triggerContext);
    }
    /**
     * 将集合转换为SQL IN子句格式的字符串
     * @param collection 需要转换的集合
     * @return 格式化后的字符串，如: 'item1', 'item2'
     */
    public static String parseListToStr2(Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        return collection.stream()
                .map(item -> "'" + item + "'")
                .collect(Collectors.joining(", "));
    }
}
