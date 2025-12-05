package other.coloplast.impl.service.inquiry;

import com.rkhd.platform.sdk.data.model.Account;
import com.rkhd.platform.sdk.data.model.Inquiry__c;
import com.rkhd.platform.sdk.data.model.User;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.XObjectService;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class InquiryService {
    private static Logger logger = LoggerFactory.getLogger();
    //更新对应询价单的客户名称(仅限搜索)
    public static void updateaccNameSearch(List<Inquiry__c> needUpdateInq, Set<Long> accIds){
        try{
            if(!accIds.isEmpty()){
                Map<Long, Account> accMap = new HashMap<>();
                List<Inquiry__c> updateInqlist = new ArrayList<>();
                String accsql = " SELECT Id, accountName FROM Account WHERE Id IN (" + StringUtils.join(accIds,",") + ")";
                QueryResult<Account> accResult = XObjectService.instance().query(accsql, true, true);
                if(accResult.getSuccess()){
                    for(Account acc : accResult.getRecords()){
                        accMap.put(acc.getId(), acc);
                    }
                }

                for(Inquiry__c inq : needUpdateInq){
                    if(accMap.containsKey(inq.getAccount__c())){
                        Inquiry__c updateinq = new Inquiry__c();
                        updateinq.setId(inq.getId());
                        updateinq.setAccName_Search__c(accMap.get(inq.getAccount__c()).getAccountName());
                        updateInqlist.add(updateinq);
                    }
                }

                if(!updateInqlist.isEmpty()){
                    BatchOperateResult batchResult = XObjectService.instance().update(updateInqlist,true,true);
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
            }
        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }
    }

    //按钮获取取消理由多选值

}
