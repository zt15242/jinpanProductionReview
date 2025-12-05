package other.coloplast.impl.common;

import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.ProType_Map__c;
import com.rkhd.platform.sdk.data.model.SalesOffice__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.XObjectService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utilities_General {
    private static Logger logger = LoggerFactory.getLogger();

    /* 根据代表处名称或事业部名称获取对应代表处或事业部负责人 */
    public static Map<String,Long> getDepartMentManagerMap(String key){
        Map<String, Long> resultMap = new HashMap<>();
        try {
            if (key.equals('0')) {
                String sql = " SELECT Id,Manager__c,Name FROM SalesOffice__c ";
                QueryResult<SalesOffice__c> soresult = XObjectService.instance().query(sql, true, true);
                if(soresult.getSuccess()){
                    List<SalesOffice__c> officeList = soresult.getRecords();
                    for (SalesOffice__c sf : officeList) {
                        resultMap.put(sf.getName(), sf.getManager__c());
                    }
                }else{
                    logger.error("soresult====" + soresult.getErrorMessage());
                }
            } else {
                String sql1 = " SELECT ID, Manager__c, Department__c FROM ProType_Map__c ";
                QueryResult<ProType_Map__c> ptresult = XObjectService.instance().query(sql1, true, true);
                if(ptresult.getSuccess()){
                    List<ProType_Map__c> departList = ptresult.getRecords();
                    for (ProType_Map__c pm : departList) {
                        resultMap.put(String.valueOf(pm.getDepartment__c()), pm.getManager__c());
                    }
                }else {
                    logger.error("soresult====" + ptresult.getErrorMessage());
                }

            }
        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }
        return resultMap;
    }
}
