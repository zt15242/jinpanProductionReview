package other.xsy.salestarget.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.*;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.XsyHttpException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.service.XoqlService;
import other.xsy.salestarget.pojo.ResponseBody;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author CaiShuLian 2025-09-24 15:21
 * @since 销售目标的触发器
 */
public class SalesTargetUtil {

    private static final Logger log = LoggerFactory.getLogger();

    /**
     * 新增或者更新销售目标后触发其他业务
     */
    public static void insertORUpdateSalesTarget(List<SalesTarget__c> salesTargetList){
        try {
            Long recordId = MetadataService.instance().getBusiType("salesTarget__c", "Business__c").getId();
            Long recordId1 = MetadataService.instance().getBusiType("salesTarget__c", "SalesAdmin__c").getId();
            Set<String> salesCodeSet = new HashSet<String>();
            Map<String,SalesOffice__c> samap = getChatterManager("0");
            Map<String,Long> demap = getDepartMentManagerMap("1");
            log.info("samap:" + samap);
            log.info("demap:" + demap);
            ObjectMetaReq objectMetaReq = ObjectMetaReq.instance().getObjectAllMeta("salesTarget__c");
            for(SalesTarget__c sat : salesTargetList){
                if (sat.getBusiness__c() == null && sat.getProduct_Type__c() != null && recordId.equals(sat.getEntityType())) {
                    sat.setBusiness__c(getControllingValue("salesTarget__c","business__c","product_Type__c",sat.getProduct_Type__c()));
                }
                log.info("sat:Business__c:" + sat.getBusiness__c());
                if (recordId.equals(sat.getEntityType()) && sat.getBusiness__c() != null) {
                    if(sat.getBusiness__c() == 11){
                        //0052v00000gFdO9
                        sat.setOwnerId(null);
                    }
                    if (demap.containsKey(sat.getBusiness__c().toString())) {
                        sat.setOwnerId(demap.get(sat.getBusiness__c().toString()));
                    }
                }
                if (sat.getArea__c() == null && sat.getOffice__c() != null) {
                    sat.setArea__c(getControllingValue("salesTarget__c","area__c","office__c",sat.getOffice__c()));
                }
                if (recordId1.equals(sat.getEntityType()) && sat.getOffice__c() != null) {
                    if (samap.containsKey(sat.getOffice__c().toString())) {
                        SalesOffice__c sao = samap.get(objectMetaReq.getObjectLabel(sat.getOffice__c().toString()));
                        if(sao != null && sao.getManager__c() != null){
                            sat.setOwnerId(sao.getManager__c());
                        }
                    }
                }
                if (sat.getSalesGroupCode__c() != null) {
                    salesCodeSet.add(sat.getSalesGroupCode__c());
                }
                log.info("sat:Area__c:" + sat.getArea__c());
            }
            log.info("salesCodeSet:" + salesCodeSet);
            //通过SAP销售组编号匹配对应的员工号
            Map<String, SalesOfficeManger__c> userMap = new HashMap<String, SalesOfficeManger__c>();
            Set<String> userIds2 = new HashSet<String>();
            Map<String, User> userMap2 = new HashMap<String, User>();

            String salesTCodes = salesCodeSet.stream().map(Object::toString).collect(Collectors.joining(","));
            String salesOfficeMangersql = "Select name,id,employeeCode__c,salesMangerCode__c FROM SalesOfficeManger__c WHERE salesMangerCode__c IN ("+salesTCodes+")";
            QueryResult<JSONObject> querySalesOfficeManger= XoqlService.instance().query(salesOfficeMangersql,true,true);
            if(querySalesOfficeManger.getRecords() != null && !querySalesOfficeManger.getRecords().isEmpty()) {
                List<SalesOfficeManger__c> salesOfficeMangerList = querySalesOfficeManger.getRecords().stream().map(json -> json.toJavaObject(SalesOfficeManger__c.class)).collect(Collectors.toList());
                for(SalesOfficeManger__c objCS : salesOfficeMangerList){
                    userMap.put(objCS.getSalesMangerCode__c(), objCS);
                    userIds2.add(objCS.getEmployeeCode__c());
                }
            }

            String userIdstr2 = userIds2.stream().map(Object::toString).collect(Collectors.joining(","));
            String usersql = "Select name,id,employeeCode FROM user WHERE employeeCode IN ("+userIdstr2+")";
            QueryResult<JSONObject> queryUser= XoqlService.instance().query(usersql,true,true);
            if(queryUser.getRecords() != null && !queryUser.getRecords().isEmpty()) {
                List<User> userList = queryUser.getRecords().stream().map(json -> json.toJavaObject(User.class)).collect(Collectors.toList());
                for(User objCS : userList){
                    userMap2.put(objCS.getEmployeeCode(), objCS);
                }
            }

            if (!salesCodeSet.isEmpty()) {
                String ussql = "Select id,salesCode__c FROM user WHERE salesCode__c IN ("+salesTCodes+")";
                QueryResult<JSONObject> queryUs= XoqlService.instance().query(ussql,true,true);
                List<User> usList = new ArrayList<User>();
                if(queryUs.getRecords() != null && !queryUs.getRecords().isEmpty()) {
                    usList = queryUs.getRecords().stream().map(json -> json.toJavaObject(User.class)).collect(Collectors.toList());
                }
                log.info("usList:" + usList);
                Map<String,Long> usIdMap = new HashMap<String,Long>();
                for (User us : usList) {
                    usIdMap.put(us.getSalesCode__c(), us.getId());
                }
                for (SalesTarget__c sat : salesTargetList) {
                    if(userMap.containsKey(sat.getSalesGroupCode__c())){
                        if( userMap.get(sat.getSalesGroupCode__c()).getEmployeeCode__c() != null
                                && userMap2.containsKey(userMap.get(sat.getSalesGroupCode__c()).getEmployeeCode__c())
                        ){
                            sat.setOwnerId(userMap2.get(userMap.get(sat.getSalesGroupCode__c()).getEmployeeCode__c()).getId());
                        }
                    }
                }
            }
            BatchOperateResult operateResult = XObjectService.instance().update(salesTargetList,true);
            log.info("批量更新结果:" + operateResult.toString());
        }catch (Exception e) {
            log.error("新增或者更新销售目标后触发其他业务异常", e);
        }
    }

    /**
     * 根据代表处名称或事业部名称获取对应分管领
     */
    public static Map<String, SalesOffice__c> getChatterManager(String key) throws ApiEntityServiceException, IOException, XsyHttpException {
        Map<String,SalesOffice__c> saMap = new HashMap<String,SalesOffice__c>();
        if ("0".equals(key)) {
            String salesOfficesql = "SELECT id,name,manager__c,saleArea__c.user2__c,saleArea__c.user__c, code__c FROM SalesOffice__c";
            QueryResult<JSONObject> querySalesOffice = XoqlService.instance().query(salesOfficesql,true,true);
            List<SalesOffice__c> officeList = querySalesOffice.getRecords().stream().map(json -> json.toJavaObject(SalesOffice__c.class)).collect(Collectors.toList());
            for(SalesOffice__c sf : officeList){
                saMap.put(sf.getName(),sf);
            }
        }
        return saMap;
    }

    /**
     * 根据代表处名称或事业部名称获取对应代表处或事业部负责人
     */
    public static Map<String,Long> getDepartMentManagerMap(String key) throws ApiEntityServiceException, IOException, XsyHttpException {
        Map<String,Long> resultMap = new HashMap<String,Long>();
        if("0".equals(key)) {
            String salesOfficesql = "SELECT id,manager__c,name FROM SalesOffice__c";
            QueryResult<JSONObject> querySalesOffice = XoqlService.instance().query(salesOfficesql,true,true);
            List<SalesOffice__c> officeList = querySalesOffice.getRecords().stream().map(json -> json.toJavaObject(SalesOffice__c.class)).collect(Collectors.toList());
            for(SalesOffice__c sf:officeList){
                resultMap.put(sf.getName(),sf.getManager__c());
            }
        } else {
            String proTypeMapsql = "SELECT id,manager__c,department__c FROM ProType_Map__c";
            QueryResult<JSONObject> querySalesOffice = XoqlService.instance().query(proTypeMapsql,true,true);
            List<ProType_Map__c> departList = querySalesOffice.getRecords().stream().map(json -> json.toJavaObject(ProType_Map__c.class)).collect(Collectors.toList());
            for(ProType_Map__c pm:departList){
                resultMap.put(pm.getDepartment__c().toString(),pm.getManager__c());
            }
        }
        return resultMap;
    }

    /**
     * 通过依赖字段找到控制字段
     * @return
     */
    public static Integer getControllingValue(String pObjName, String pControllingFieldName, String pDependentFieldName, Integer pDependentValue) throws IOException, XsyHttpException {
        ResponseBody responseBody;
        RkhdHttpData data = new RkhdHttpData();
        data.setCall_type("GET");
        String url = "/rest/metadata/v2.0/xobjects/" + pObjName + "/itemDependencies/itemItems/controlItems/" + pControllingFieldName + "/" + pDependentFieldName;
        data.setCallString(url);
        RkhdHttpClient client = RkhdHttpClient.instance();
        responseBody = client.execute(data, (dataString) -> {
            log.info("[查询依赖字段]获取的结果:"+ dataString );
            try {
                return JSON.parseObject(dataString, ResponseBody.class);
            }catch (Exception e){
                log.error("[查询依赖字段]获取的结果转换失败:"+ dataString);
                throw new RuntimeException("[查询依赖字段]获取的结果转换失败:"+ dataString);
            }
        });
        if ("0".equals(responseBody.getCode())) {
            List<Map<String, Object>> list = new ArrayList<>();
            if(responseBody.getData() != null){
                JSONObject rData = (JSONObject) responseBody.getData();
                JSONObject records = rData.getJSONObject("records");
                if(records != null && !records.isEmpty()){
                    JSONArray itemDependencies = records.getJSONArray("itemDependencies");
                    if(itemDependencies != null && !itemDependencies.isEmpty()){
                        JSONArray itemDependencyDetailAOs = itemDependencies.getJSONObject(0).getJSONArray("itemDependencyDetailAOs");
                        if(itemDependencyDetailAOs != null && !itemDependencyDetailAOs.isEmpty()){
                            for(int i = 0; i < itemDependencyDetailAOs.size(); i++) {
                                JSONObject itemDependencyDetailAO = itemDependencyDetailAOs.getJSONObject(i);
                                if(itemDependencyDetailAO != null){
                                    List<Integer> dependentItemCodeList = Arrays.stream(itemDependencyDetailAO.getString("dependentItemCodeList").split(","))
                                            .map(Integer::parseInt)
                                            .collect(Collectors.toList());;
                                    if(!dependentItemCodeList.isEmpty()){
                                        if(dependentItemCodeList.contains(pDependentValue)){
                                             return itemDependencyDetailAO.getInteger("controlItemCode");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException, XsyHttpException {
        //Integer controllingValue = getControllingValue("salesTarget__c","business__c","product_Type__c",11);
        //System.out.println(controllingValue);
        try {
            //getChatterManager("0");
            getDepartMentManagerMap("1");
        }catch (Exception e){

        }
    }
}
