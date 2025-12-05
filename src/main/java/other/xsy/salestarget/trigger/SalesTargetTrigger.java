package other.xsy.salestarget.trigger;

import com.alibaba.fastjson.JSON;
import com.rkhd.platform.sdk.data.model.*;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.CustomConfigException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.model.XObject;
import com.rkhd.platform.sdk.service.CustomConfigService;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.trigger.*;
import other.xsy.salestarget.util.NeoCrmRkhdService;
import other.xsy.salestarget.util.ObjectOptionValueRetrieval;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 武于伦
 * @version 1.0
 * @email 2717718875@qq.com
 * @data 2025/9/19 23:36
 */
public class SalesTargetTrigger implements Trigger {
    private static final Logger logger = LoggerFactory.getLogger();
    @Override
    public TriggerResponse execute(TriggerRequest triggerRequest) throws ScriptBusinessException {
        List<DataResult> result = new ArrayList<>();
        String msg ="";
        TriggerContext triggerContext = new TriggerContext();
        Boolean success = true;
        try {
            List<XObject> xObjectList = triggerRequest.getDataList();
            List<Long> collect = xObjectList.stream().map(sa -> ((SalesTarget__c) sa).getId()).collect(Collectors.toList());
            SalesTargetRepeatCheckHandler(collect);
            for (XObject xObject : xObjectList) {
                result.add(new DataResult(true, "",xObject));
            }
        } catch (ApiEntityServiceException e) {
            success = false;
            msg = e.getMessage();
            logger.error(msg);
        } catch (CustomConfigException e) {
            success = false;
            msg = e.getMessage();
            logger.error(msg);
        }
        return  new TriggerResponse(success, msg, result, triggerContext);
    }

    public static void SalesTargetRepeatCheckHandler(List<Long> collect) throws ApiEntityServiceException, CustomConfigException {
        String sql = "select id,business__c,product_Type__c,entityType,area__c,office__c,salesGroupCode__c from salesTarget__c where id in (" + parseListToStr2(collect) + ")";
        Long recordId = MetadataService.instance().getBusiType("salesTarget__c", "Business__c").getId();
        Long recordId1 = MetadataService.instance().getBusiType("salesTarget__c", "SalesAdmin__c").getId();
        ArrayList<SalesTarget__c> salesTargetList = new ArrayList<>();
        Map<String,SalesOffice__c> samap = getChatterManager("0");
        Map<String,Long> demap = getDepartMentManagerMap("1");
        Set<String> salesCodeSet = new HashSet<String>();
        logger.info("*** demap: "+demap);
        QueryResult<SalesTarget__c> query = XObjectService.instance().query(sql, true, true);
        logger.info("*** query: "+JSON.toJSONString(query.getRecords()));
        Map<String, List<String>> controllingValue = NeoCrmRkhdService.getControllingValue(null, "salesTarget__c", "business__c", "product_Type__c");
        Map<String, List<String>> controllingValue1 = NeoCrmRkhdService.getControllingValue(null, "salesTarget__c", "area__c", "office__c");
        for (SalesTarget__c sat : query.getRecords()) {
            if (sat.getBusiness__c() == null && sat.getProduct_Type__c() != null && sat.getEntityType().equals(recordId) ) {
                if (controllingValue.entrySet().size()>0){
                    for (Map.Entry<String, List<String>> stringListEntry : controllingValue.entrySet()) {
                        for (String s1 :stringListEntry.getValue()){
                            if (s1.equals(sat.getProduct_Type__c())){
                                sat.setBusiness__c(Integer.parseInt(stringListEntry.getKey()));
                            }
                        }
                    }
                }
            }
            logger.info("*** controllingValue: "+sat.getBusiness__c());
            logger.info("类型："+sat.getEntityType()+"===="+recordId);
            if (sat.getEntityType().equals(recordId) && sat.getBusiness__c() != null) {
                if (sat.getBusiness__c() != null && sat.getBusiness__c().equals(11) ) {
                    Map<String, String> salesTargetOwnerId = CustomConfigService.instance().getConfigSet("salesTargetOwnerId");
                    String s = salesTargetOwnerId.get("OwnerId");
                    logger.info("*** salesTargetOwnerId: "+s);
                    sat.setOwnerId(Long.valueOf(s));
                }
                if (demap.containsKey(sat.getBusiness__c())) {
                    sat.setOwnerId(demap.get(sat.getBusiness__c()));
                }
                logger.info("赋值了所以人1："+sat.getOwnerId());
            }
            if (sat.getArea__c() == null && sat.getOffice__c() != null) {
                if (controllingValue1.entrySet().size() > 0){
                    for (Map.Entry<String, List<String>> stringListEntry : controllingValue1.entrySet()) {
                        for (String s : stringListEntry.getValue()) {
                            if (s.equals(sat.getOffice__c())) {
                                sat.setArea__c(Integer.parseInt(stringListEntry.getKey()));
                            }
                        }
                    }
                }
            }
            if (sat.getEntityType().equals(recordId1)&& sat.getOffice__c() != null) {
                Map<String, String> stringIntegerMap = ObjectOptionValueRetrieval.objectInformation("salesTarget__c", "office__c");
                if (samap.containsKey(stringIntegerMap.get(sat.getOffice__c()))) {
                    SalesOffice__c sao = samap.get(salesTargetList.get(sat.getOffice__c()));
                    sat.setOwnerId(sao.getManager__c());
                }
            }
            if (sat.getSalesGroupCode__c() !=null) {
                salesCodeSet.add(sat.getSalesGroupCode__c());
            }
            logger.info("*** sat.Area__c: "+sat.getArea__c());
            logger.info("*** OwnerId: "+sat.getOwnerId());
            salesTargetList.add(sat);
        }
        logger.info("*** salesCodeSet: "+salesCodeSet);
        if (salesCodeSet.size()>0){
            Map<String, SalesOfficeManger__c> userMap = new HashMap<String, SalesOfficeManger__c>(); //通过SAP销售组编号匹配对应的员工号
            Set<String> userIds2=new HashSet<String>();
            Map<String, User> userMap2 = new HashMap<String, User>();
            String salesOfficeMangerSql = "Select name, id,employeeCode__c,salesMangerCode__c From SalesOfficeManger__c WHERE salesMangerCode__c in ("+parseListToStr2(salesCodeSet)+")";
            QueryResult<SalesOfficeManger__c> query1 = XObjectService.instance().query(salesOfficeMangerSql, true, true);
            if (query1.getTotalCount() > 0){
                for (SalesOfficeManger__c objCS : query1.getRecords()) {
                    userMap.put(objCS.getSalesMangerCode__c(), objCS);
                    userIds2.add(objCS.getEmployeeCode__c());
                }
            }
            String userSql = "select id,name,employeeCode from user where employeeCode in("+parseListToStr2(userIds2)+")";
            QueryResult<User> query2 = XObjectService.instance().query(userSql, true, true);
            if (query2.getTotalCount() > 0){
                for (User objCS : query2.getRecords()) {
                    userMap2.put(objCS.getEmployeeCode(), objCS);
                }
            }
            if (salesCodeSet.size()>0) {
                String userSql2 = "select id,salesCode__c from user where salesCode__c in ("+parseListToStr2(salesCodeSet)+")";
                QueryResult<User> query3 = XObjectService.instance().query(userSql2, true, true);
                logger.info("*** query3: "+ JSON.toJSONString(query3.getRecords()));
                Map<String,Long> usIdMap = new HashMap<String,Long>();
                for (User us : query3.getRecords()) {
                    usIdMap.put(us.getSalesCode__c(),us.getId());
                }
                for (SalesTarget__c sat : query.getRecords()) {
                    if (userMap.containsKey(sat.getSalesGroupCode__c())){
                        if (userMap.get(sat.getSalesGroupCode__c()).getEmployeeCode__c() != null
                                && userMap2.containsKey(userMap.get(sat.getSalesGroupCode__c()).getEmployeeCode__c())){
                            sat.setOwnerId(userMap2.get(userMap.get(sat.getSalesGroupCode__c()).getEmployeeCode__c()).getId());
                        }
                    }
                    logger.info("*** OwnerId2: "+sat.getOwnerId());
                    salesTargetList.add(sat);
                }
            }
        }
        logger.info("update:"+JSON.toJSONString(salesTargetList));
        BatchOperateResult update = XObjectService.instance().update(salesTargetList, true, true);
        if (!update.getSuccess()){
            throw new RuntimeException("更新失败:"+update.getErrorMessage());
        }
    }

    /* 根据代表处名称或事业部名称获取对应分管领导 */
    public static Map<String, SalesOffice__c> getChatterManager(String key) throws ApiEntityServiceException {
        Map<String,SalesOffice__c> saMap = new HashMap<>();
        if (key == "0") {
            String sql = "select id,name,manager__c,code__c from SalesOffice__c";
            QueryResult<SalesOffice__c> query = XObjectService.instance().query(sql, true,true);
            if (query.getTotalCount()>0){
                for (SalesOffice__c record : query.getRecords()) {
                    saMap.put(record.getName(), record);
                }
            }
        }
        return saMap;
    }

    /* 根据代表处名称或事业部名称获取对应代表处或事业部负责人 */
    public static Map<String,Long> getDepartMentManagerMap(String key) throws ApiEntityServiceException {
        Map<String,Long> resultMap=new HashMap<>();
        if(key=="0") {
            String sql = "select id,manager__c,name from SalesOffice__c";
            QueryResult<SalesOffice__c> query = XObjectService.instance().query(sql, true,true);

            for(SalesOffice__c sf:query.getRecords()){
                resultMap.put(sf.getName(),sf.getManager__c());
            }
        }
        else {
            String sql = "select id,manager__c,department__c from ProType_Map__c";
            QueryResult<ProType_Map__c> query = XObjectService.instance().query(sql, true,true);
            for(ProType_Map__c pm:query.getRecords()){
                resultMap.put(pm.getDepartment__c().toString(),pm.getManager__c());
            }
        }
        return resultMap;
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
