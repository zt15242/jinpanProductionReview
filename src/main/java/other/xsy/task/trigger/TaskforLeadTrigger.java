package other.xsy.task.trigger;

import com.alibaba.fastjson.JSON;
import com.rkhd.platform.sdk.data.model.Lead;
import com.rkhd.platform.sdk.data.model.Task;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.trigger.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TaskforLeadTrigger implements Trigger {

    private static final Logger logger = LoggerFactory.getLogger();

    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {
        List<DataResult> result = new ArrayList<>();
        String msg ="";
        TriggerContext triggerContext = new TriggerContext();
        Boolean success = true;
        try {
            logger.info("除非task任务触发器");
            List<XObject> xObjectList = triggerRequest.getDataList();
            List<Long> clueFollowUpTasks = xObjectList.stream()
                    .filter(task -> ((Task) task).getTaskName().endsWith("线索跟进任务"))
                    .filter(task -> ((Task) task).getStatus() == 2)
                    .filter(task -> ((Task) task).getEntityObjectId() != null)
                    .map(task -> ((Task) task).getEntityObjectId())
                    .collect(Collectors.toList());
            logger.info("任务："+ JSON.toJSONString(clueFollowUpTasks));
            if (clueFollowUpTasks.size() > 0) {
                String sql = "select id,status from lead where id  in (" + parseListToStr2(clueFollowUpTasks)+") and status = 2";
                logger.info("sql:"+sql);
                QueryResult<XObject> query = XObjectService.instance().query(sql, true, true);
                ArrayList<Lead> leads = new ArrayList<>();
                if (query.getTotalCount() > 0) {
                    for (XObject record : query.getRecords()) {
                        ((Lead) record).setStatus(3);
                        leads.add((Lead) record);
                        result.add(new DataResult(true, "",record));
                    }
                    logger.info("leads:" + JSON.toJSONString(leads));
                    BatchOperateResult update = XObjectService.instance().update(leads);
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
