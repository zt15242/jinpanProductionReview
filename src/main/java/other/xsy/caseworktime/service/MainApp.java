package other.xsy.caseworktime.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.api.annotations.RequestMethod;
import com.rkhd.platform.sdk.api.annotations.RestApi;
import com.rkhd.platform.sdk.api.annotations.RestMapping;
import com.rkhd.platform.sdk.api.annotations.RestBeanParam;
import com.rkhd.platform.sdk.data.model.CaseMonTime__c;
import com.rkhd.platform.sdk.data.model.Case_WorkOrder__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.BatchOperateResult;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.service.FutureTaskService;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.service.XoqlService;
import other.xsy.caseworktime.controller.CaseWorkTimeController;
import other.xsy.caseworktime.controller.XoqlCaseRepository;
import other.xsy.caseworktime.futuretask.FutureTaskDemoImpl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RestApi(baseUrl = "/service/apexrest")
public class MainApp {

    private static final Logger logger = LoggerFactory.getLogger();

    @RestMapping(value = "/GetCase",method = RequestMethod.GET)
    public String GetCase() throws ApiEntityServiceException {
        // 1) 组装上下文（仓储与事务管理）
        CaseWorkTimeController.CaseWorkTimeContext context = new CaseWorkTimeController.CaseWorkTimeContext() {
            private final CaseWorkTimeController.CaseRepository caseRepo = new XoqlCaseRepository();
            private final CaseWorkTimeController.CaseMonTimeRepository cmtRepo = new InMemoryCaseMonTimeRepository(); // 示例：用内存实现或替换为你的 XOQL 实现
            private final CaseWorkTimeController.CaseWorkOrderRepository cwoRepo = new InMemoryCaseWorkOrderRepository(); // 示例：用内存实现或替换为你的 XOQL 实现
            private final CaseWorkTimeController.TransactionManager tx = runnable -> {
                // 简单直跑；如用 Spring，改成 TransactionTemplate.execute
                runnable.run();
            };

            @Override public CaseWorkTimeController.CaseRepository caseRepository() { return caseRepo; }
            @Override public CaseWorkTimeController.CaseMonTimeRepository caseMonTimeRepository() { return cmtRepo; }
            @Override public CaseWorkTimeController.CaseWorkOrderRepository caseWorkOrderRepository() { return cwoRepo; }
            @Override public CaseWorkTimeController.TransactionManager transactionManager() { return tx; }
        };

        // 2) 调用 getCase
        CaseWorkTimeController.PickClass result = CaseWorkTimeController.getCase(context);
        logger.info(JSON.toJSONString(result));
        // 3) 使用结果
        logger.info("当月个案数: " + (result.casMonTList == null ? 0 : result.casMonTList.size()));
        logger.info("跨月个案数: " + (result.casMonTList2 == null ? 0 : result.casMonTList2.size()));
        return JSON.toJSONString(result);
    }

    @RestMapping(value = "/SaveCase",method = RequestMethod.POST)
    public String SaveCase(@RestBeanParam(name = "json") JSONObject json) throws ApiEntityServiceException {
        ArrayList<CaseMonTime__c> saveList = new ArrayList<>();
        ArrayList<Case_WorkOrder__c> saveList2 = new ArrayList<>();
        HashMap<String, Case_WorkOrder__c> cHashMap = new HashMap<>();
        JSONObject resp = new JSONObject();
        Long casemtid = MetadataService.instance().getBusiType("caseMonTime__c", "defaultBusiType").getId();
        // 解析json
        try {
            JSONArray TimeList = json.getJSONArray("casMonTList");
            JSONArray OverTimeList = json.getJSONArray("casMonTList2");
            // 遍历 casMonTList
            if (TimeList != null) {
                for (int i = 0; i < TimeList.size(); i++) {
                    JSONObject item = TimeList.getJSONObject(i);
                    CaseWorkTimeController.InnerClass inner = parseInner(item);
                    if (inner != null && inner.casMonTime != null) {
                        logger.info("保存-当月: caseNumber=" + inner.casMonTime.caseNumber
                                + ", workTime=" + (inner.casMonTime.workTime == null ? null : inner.casMonTime.workTime.value));
                        CaseMonTime__c caseMonTime__c = new CaseMonTime__c();
                        caseMonTime__c.setCase__c(Long.parseLong(inner.casMonTime.caseId));
                        BigDecimal value = inner.casMonTime.workTime.value;
                        caseMonTime__c.setWorkTime__c(value.doubleValue());
                        caseMonTime__c.setCaseNumber__c(inner.casMonTime.caseNumber);
                        caseMonTime__c.setEntityType(casemtid);
                        saveList.add(caseMonTime__c);
                    }
                    if (inner.cwoMonTime != null && inner.cwoMonTime.size()>0 ) {
                        for (CaseWorkTimeController.CaseWorkOrder caseWorkOrder : inner.cwoMonTime) {
                            Case_WorkOrder__c caseWorkOrder__c = new Case_WorkOrder__c();
                            caseWorkOrder__c.setWorkOrder__c(Long.valueOf(caseWorkOrder.workOrderId));
                            caseWorkOrder__c.setTotalTime__c(caseWorkOrder.totalTime.value.doubleValue());
                            caseWorkOrder__c.setReadlyTime__c(caseWorkOrder.readlyTime.value.doubleValue());
                            caseWorkOrder__c.setDealTime__c(caseWorkOrder.dealTime.value.doubleValue());
                            caseWorkOrder__c.setStatus__c(caseWorkOrder.status);
                            caseWorkOrder__c.setWorkOrderNumber__c(caseWorkOrder.workOrderNumber);
                            caseWorkOrder__c.setWork_RecordType__c(caseWorkOrder.workRecordType);
                            caseWorkOrder__c.setAlredy_Time__c(caseWorkOrder.alreadyTime.value.doubleValue());
                            cHashMap.put(inner.casMonTime.caseNumber+'-'+caseWorkOrder.workOrderId,caseWorkOrder__c);
                        }
                    }
                }
            }

            // 遍历 casMonTList2（跨月）
            if (OverTimeList != null) {
                for (int i = 0; i < OverTimeList.size(); i++) {
                    JSONObject item = OverTimeList.getJSONObject(i);
                    CaseWorkTimeController.InnerClass inner = parseInner(item);
                    if (inner != null && inner.casMonTime != null) {
                        logger.info("保存-跨月: caseNumber=" + inner.casMonTime.caseNumber
                                + ", workTime=" + (inner.casMonTime.workTime == null ? null : inner.casMonTime.workTime.value));
                        CaseMonTime__c caseMonTime__c = new CaseMonTime__c();
                        caseMonTime__c.setCase__c(Long.parseLong(inner.casMonTime.caseId));
                        BigDecimal value = inner.casMonTime.workTime.value;
                        caseMonTime__c.setWorkTime__c(value.doubleValue());
                        caseMonTime__c.setCaseNumber__c(inner.casMonTime.caseNumber);
                        caseMonTime__c.setEntityType(casemtid);
                        saveList.add(caseMonTime__c);
                    }
                    if (inner.cwoMonTime != null && inner.cwoMonTime.size()>0 ) {
                        for (CaseWorkTimeController.CaseWorkOrder caseWorkOrder : inner.cwoMonTime) {
                            Case_WorkOrder__c caseWorkOrder__c = new Case_WorkOrder__c();
                            caseWorkOrder__c.setWorkOrder__c(Long.valueOf(caseWorkOrder.workOrderId));
                            caseWorkOrder__c.setTotalTime__c(caseWorkOrder.totalTime.value.doubleValue());
                            caseWorkOrder__c.setReadlyTime__c(caseWorkOrder.readlyTime.value.doubleValue());
                            caseWorkOrder__c.setDealTime__c(caseWorkOrder.dealTime.value.doubleValue());
                            caseWorkOrder__c.setStatus__c(caseWorkOrder.status);
                            caseWorkOrder__c.setWorkOrderNumber__c(caseWorkOrder.workOrderNumber);
                            caseWorkOrder__c.setWork_RecordType__c(caseWorkOrder.workRecordType);
                            caseWorkOrder__c.setAlredy_Time__c(caseWorkOrder.alreadyTime.value.doubleValue());
                            cHashMap.put(inner.casMonTime.caseNumber+'-'+caseWorkOrder.workOrderId,caseWorkOrder__c);
                        }
                    }
                }
            }
            Boolean success = false;
            String message = "";
            if (saveList.size() > 0) {
                // 开始进行异步操作
                JSONObject jsonObject = new JSONObject();
                 // saveList 每 50条 一批次
                for (int i = 0; i < saveList.size(); i += 50) {
                    List<CaseMonTime__c> batch = saveList.subList(i, Math.min(i + 50, saveList.size()));
                    jsonObject.put("saveList", batch);
                    jsonObject.put("saveList2", cHashMap);
                    // 调用 异步
                    FutureTaskService.instance().addFutureTask(FutureTaskDemoImpl.class,jsonObject.toJSONString());
                }


//                BatchOperateResult insert = XObjectService.instance().insert(saveList, false, true);
                // 判断是否全部插入成功
//                if (insert.getSuccess()){
//                    // 根据顺序匹配上id
//                    List<OperateResult> operateResults = insert.getOperateResults();
//                    for (int i = 0; i < operateResults.size() && i < saveList.size(); i++) {
//                        OperateResult result = operateResults.get(i);
//                        if (result.getSuccess()) {
//                            CaseMonTime__c caseMonTime = saveList.get(i);
//                            caseMonTime.setId(result.getDataId());
//                            // 根据caseNumber找到对应的Case_WorkOrder__c，设置关联字段
//                            String caseNumber = caseMonTime.getCaseNumber__c();
//                            for (String key : cHashMap.keySet()) {
//                                if (key.startsWith(caseNumber)) {
//                                    Case_WorkOrder__c workOrder = cHashMap.get(key);
//                                    workOrder.setCaseMonTime__c(result.getDataId());
//                                    saveList2.add(workOrder);
//                                }
//                            }
//                        }
//                    }
//                }else {
//                    message = insert.getErrorMessage();
//                    throw new Exception(message);
//                }

            }
//            if (saveList2.size() > 0){
//                BatchOperateResult insert1 = XObjectService.instance().insert(saveList2, false, true);
//                success = insert1.getSuccess();
//                message = insert1.getErrorMessage();
//                if (!insert1.getSuccess()){
//                    message = insert1.getErrorMessage();
//                    throw new Exception(message);
//                }
//            }
//            if (success){
                resp.put("code", 200);
                resp.put("message", "ok");
//            }
        }catch (Exception e) {
            resp.put("code", 500);
            resp.put("message", e.getMessage());
        }
        return resp.toJSONString();
    }

    private static CaseWorkTimeController.InnerClass parseInner(JSONObject obj) {
        CaseWorkTimeController.InnerClass inner = new CaseWorkTimeController.InnerClass();
        if (obj == null) return inner;

        // inOrderNum
        inner.inOrderNum = obj.getInteger("inOrderNum");

        // casMonTime
        JSONObject cmt = obj.getJSONObject("casMonTime");
        if (cmt != null) {
            CaseWorkTimeController.CaseMonTime m = new CaseWorkTimeController.CaseMonTime();
            m.id = cmt.getString("id");
            m.caseId = cmt.getString("caseId");
            m.caseNumber = cmt.getString("caseNumber");
            m.status = cmt.getString("status");
            m.productType = cmt.getString("productType");
            String createdDate = cmt.getString("createdDate");
            String expectFinishDate = cmt.getString("expectFinishDate");
            if (createdDate != null && createdDate.length() >= 10) {
                m.createdDate = java.time.LocalDate.parse(createdDate.substring(0,10));
            }
            if (expectFinishDate != null && expectFinishDate.length() >= 10) {
                m.expectFinishDate = java.time.LocalDate.parse(expectFinishDate.substring(0,10));
            }
            m.workTime = toDecimal(cmt.getJSONObject("workTime"));
            m.readlyTime = toDecimal(cmt.getJSONObject("readlyTime"));
            m.totalWorkTimeTotal = toDecimal(cmt.getJSONObject("totalWorkTimeTotal"));
            inner.casMonTime = m;
        }

        // cwoMonTime
        JSONArray cwoArr = obj.getJSONArray("cwoMonTime");
        java.util.List<CaseWorkTimeController.CaseWorkOrder> list = new java.util.ArrayList<>();
        if (cwoArr != null) {
            for (int i = 0; i < cwoArr.size(); i++) {
                JSONObject w = cwoArr.getJSONObject(i);
                CaseWorkTimeController.CaseWorkOrder cwo = new CaseWorkTimeController.CaseWorkOrder();
                cwo.id = w.getString("id");
                cwo.caseMonTimeId = w.getString("caseMonTimeId");
                cwo.workOrderId = w.getString("workOrderId");
                cwo.status = w.getString("status");
                cwo.workOrderNumber = w.getString("workOrderNumber");
                cwo.workRecordType = w.getString("workRecordType");
                cwo.totalTime = toDecimal(w.getJSONObject("totalTime"));
                cwo.readlyTime = toDecimal(w.getJSONObject("readlyTime"));
                cwo.dealTime = toDecimal(w.getJSONObject("dealTime"));
                cwo.alreadyTime = toDecimal(w.getJSONObject("alreadyTime"));
                list.add(cwo);
            }
        }
        inner.cwoMonTime = list;
        return inner;
    }

    private static CaseWorkTimeController.Decimal toDecimal(JSONObject obj) {
        if (obj == null) return new CaseWorkTimeController.Decimal(0);
        java.math.BigDecimal v = obj.getBigDecimal("value");
        if (v == null) return new CaseWorkTimeController.Decimal(0);
        return new CaseWorkTimeController.Decimal(v);
    }

    // 查询
//    public static void main(String[] args) throws ApiEntityServiceException {
//        // 1) 组装上下文（仓储与事务管理）
//        CaseWorkTimeController.CaseWorkTimeContext context = new CaseWorkTimeController.CaseWorkTimeContext() {
//            private final CaseWorkTimeController.CaseRepository caseRepo = new XoqlCaseRepository();
//            private final CaseWorkTimeController.CaseMonTimeRepository cmtRepo = new InMemoryCaseMonTimeRepository(); // 示例：用内存实现或替换为你的 XOQL 实现
//            private final CaseWorkTimeController.CaseWorkOrderRepository cwoRepo = new InMemoryCaseWorkOrderRepository(); // 示例：用内存实现或替换为你的 XOQL 实现
//            private final CaseWorkTimeController.TransactionManager tx = runnable -> {
//                // 简单直跑；如用 Spring，改成 TransactionTemplate.execute
//                runnable.run();
//            };
//
//            @Override public CaseWorkTimeController.CaseRepository caseRepository() { return caseRepo; }
//            @Override public CaseWorkTimeController.CaseMonTimeRepository caseMonTimeRepository() { return cmtRepo; }
//            @Override public CaseWorkTimeController.CaseWorkOrderRepository caseWorkOrderRepository() { return cwoRepo; }
//            @Override public CaseWorkTimeController.TransactionManager transactionManager() { return tx; }
//        };
//
//        // 2) 调用 getCase
//        CaseWorkTimeController.PickClass result = CaseWorkTimeController.getCase(context);
//        System.out.println(JSON.toJSONString(result));
//        // 3) 使用结果
//        System.out.println("当月个案数: " + (result.casMonTList == null ? 0 : result.casMonTList.size()));
//        System.out.println("跨月个案数: " + (result.casMonTList2 == null ? 0 : result.casMonTList2.size()));
//
//        // 4) 如需保存，调用 save
//        // String msg = CaseWorkTimeController.save(context, result.casMonTList, result.casMonTList2);
//        // System.out.println("保存结果: " + msg);
//    }
    public static void main(String[] args) throws ApiEntityServiceException {
        String json = "{\"casMonTList\":[{\"cwoMonTime\":[{\"alreadyTime\":{\"zero\":true,\"value\":0},\"readlyTime\":{\"zero\":true,\"value\":0},\"dealTime\":{\"zero\":true,\"value\":0},\"totalTime\":{\"zero\":true,\"value\":0},\"workRecordType\":\"11010045800002\",\"workOrderNumber\":\"WO-2510240001\",\"workOrderId\":\"4039082867672079\",\"status\":\"1\"}],\"inOrderNum\":2,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-10-05\",\"caseNumber\":\"GA001065\",\"caseId\":\"4011838168270944\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-11-02\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"待受理\"}},{\"cwoMonTime\":[{\"alreadyTime\":{\"zero\":true,\"value\":0},\"readlyTime\":{\"zero\":true,\"value\":0},\"dealTime\":{\"zero\":true,\"value\":0},\"totalTime\":{\"zero\":true,\"value\":0},\"workRecordType\":\"11010045800002\",\"workOrderNumber\":\"WO-2510240003\",\"workOrderId\":\"4039103915295769\",\"status\":\"1\"}],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-10-24\",\"caseNumber\":\"GA001462\",\"caseId\":\"4039098795033618\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-11-02\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已完成\"}}],\"casMonTList2\":[{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-17\",\"caseNumber\":\"GA001008\",\"caseId\":\"3987002215712835\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"SVG静止无功发生器\",\"status\":\"待受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-07-18\",\"caseNumber\":\"250718-00001\",\"caseId\":\"3900668410954800\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":960},\"workTime\":{\"zero\":false,\"value\":960},\"status\":\"待受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-08\",\"caseNumber\":\"GA001001\",\"caseId\":\"3974159063548950\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":960},\"workTime\":{\"zero\":false,\"value\":960},\"status\":\"待受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-18\",\"caseNumber\":\"GA001015\",\"caseId\":\"3988451116271725\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（出口）\",\"status\":\"已评估\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-25\",\"caseNumber\":\"GA001045\",\"caseId\":\"3998106053264425\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-06\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":3,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-29\",\"caseNumber\":\"GA001061\",\"caseId\":\"4003936624740432\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-28\",\"caseNumber\":\"GA001050\",\"caseId\":\"4001988635691073\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":6,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-28\",\"caseNumber\":\"GA001052\",\"caseId\":\"4002196367475736\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-12\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已评估\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-17\",\"caseNumber\":\"GA001009\",\"caseId\":\"3987010566030383\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-25\",\"caseNumber\":\"GA001041\",\"caseId\":\"3998040994448422\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-19\",\"caseNumber\":\"GA001016\",\"caseId\":\"3989424804004871\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"status\":\"待受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":7,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-28\",\"caseNumber\":\"GA001058\",\"caseId\":\"4002287014594647\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-07\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":680},\"createdDate\":\"2025-09-04\",\"caseNumber\":\"GA024647\",\"caseId\":\"4034944030639118\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":340},\"expectFinishDate\":\"2025-09-14\",\"workTime\":{\"zero\":false,\"value\":-340},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":5,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-29\",\"caseNumber\":\"GA001062\",\"caseId\":\"4003941631198257\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（出口）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":2,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-26\",\"caseNumber\":\"GA001047\",\"caseId\":\"3999834508018720\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":960},\"workTime\":{\"zero\":false,\"value\":960},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-05\",\"caseNumber\":\"GA024672\",\"caseId\":\"4034944029705230\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-15\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-02\",\"caseNumber\":\"GA024611\",\"caseId\":\"4034944029705238\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-12\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":960},\"createdDate\":\"2025-09-02\",\"caseNumber\":\"GA024607\",\"caseId\":\"4034944030525524\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-08\",\"workTime\":{\"zero\":false,\"value\":-720},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-04\",\"caseNumber\":\"GA024634\",\"caseId\":\"4034943641880659\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-17\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"储能一体化箱变\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":2,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-03\",\"caseNumber\":\"GA024625\",\"caseId\":\"4034944031737932\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-12\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":960},\"createdDate\":\"2025-09-02\",\"caseNumber\":\"GA024609\",\"caseId\":\"4034944031540266\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":480},\"expectFinishDate\":\"2025-09-19\",\"workTime\":{\"zero\":false,\"value\":-480},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-22\",\"caseNumber\":\"GA024951\",\"caseId\":\"4034944031737943\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-01\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"树脂浇注干式变压器（国内）\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":960},\"createdDate\":\"2025-09-02\",\"caseNumber\":\"GA024596\",\"caseId\":\"4034944030918731\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":480},\"expectFinishDate\":\"2025-09-11\",\"workTime\":{\"zero\":false,\"value\":-480},\"productType\":\"一体化逆变并网装置\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-01\",\"caseNumber\":\"GA024589\",\"caseId\":\"4034944032048128\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-11\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-22\",\"caseNumber\":\"GA024981\",\"caseId\":\"4034944032753762\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-04\",\"caseNumber\":\"GA024633\",\"caseId\":\"4034944030639132\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-18\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-10\",\"caseNumber\":\"GA024746\",\"caseId\":\"4034924709774370\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-20\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"VPI电抗器(变压器)\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-10\",\"caseNumber\":\"GA024740\",\"caseId\":\"4034944027920487\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-19\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-09\",\"caseNumber\":\"GA024726\",\"caseId\":\"4034944031737942\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-18\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"箱式变电站\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-07\",\"caseNumber\":\"GA024679\",\"caseId\":\"4034926116946947\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-16\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"储能一体化箱变\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-09\",\"caseNumber\":\"GA024717\",\"caseId\":\"4034944030753838\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-18\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-02\",\"caseNumber\":\"GA024606\",\"caseId\":\"4034944031737933\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-11\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"一体化逆变并网装置\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-01\",\"caseNumber\":\"GA024592\",\"caseId\":\"4034944030525519\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-11\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-05\",\"caseNumber\":\"GA024651\",\"caseId\":\"4034926116946946\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-08\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":4,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-19\",\"caseNumber\":\"GA024916\",\"caseId\":\"4034944031540262\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-23\",\"caseNumber\":\"GA024998\",\"caseId\":\"4034944030639148\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-17\",\"caseNumber\":\"GA024886\",\"caseId\":\"4034944027920463\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-25\",\"caseNumber\":\"GA025023\",\"caseId\":\"4034944031737969\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-04\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-10\",\"caseNumber\":\"GA024738\",\"caseId\":\"4034944030525530\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-28\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":3,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-25\",\"caseNumber\":\"GA025029\",\"caseId\":\"4034944030918721\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-08\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已受理\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":240},\"createdDate\":\"2025-09-12\",\"caseNumber\":\"GA024792\",\"caseId\":\"4034944031540269\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":960},\"expectFinishDate\":\"2025-10-10\",\"workTime\":{\"zero\":false,\"value\":720},\"productType\":\"SVG静止无功发生器\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-19\",\"caseNumber\":\"GA024928\",\"caseId\":\"4034943641880660\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-28\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-16\",\"caseNumber\":\"GA024862\",\"caseId\":\"4034944032753746\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-02\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-08\",\"caseNumber\":\"GA024703\",\"caseId\":\"4034944031540273\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-25\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-05\",\"caseNumber\":\"GA024661\",\"caseId\":\"4034944030639115\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-14\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-29\",\"caseNumber\":\"GA025094\",\"caseId\":\"4034944031540272\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-18\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-26\",\"caseNumber\":\"GA025048\",\"caseId\":\"4034944030753833\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":480},\"expectFinishDate\":\"2025-10-06\",\"workTime\":{\"zero\":false,\"value\":480},\"productType\":\"一体化逆变并网装置\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":240},\"createdDate\":\"2025-09-09\",\"caseNumber\":\"GA024729\",\"caseId\":\"4034944031540263\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":120},\"expectFinishDate\":\"2025-09-16\",\"workTime\":{\"zero\":false,\"value\":-120},\"productType\":\"VPI电抗器(变压器)\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-16\",\"caseNumber\":\"GA024854\",\"caseId\":\"4034944030525525\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-26\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":1080},\"createdDate\":\"2025-09-10\",\"caseNumber\":\"GA024745\",\"caseId\":\"4034944028886021\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":1260},\"expectFinishDate\":\"2025-10-10\",\"workTime\":{\"zero\":false,\"value\":180},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-19\",\"caseNumber\":\"GA024919\",\"caseId\":\"4034944027739181\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-10\",\"caseNumber\":\"GA024749\",\"caseId\":\"4034920039728161\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-08\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"储能一体化箱变\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-05\",\"caseNumber\":\"GA024668\",\"caseId\":\"4034944030639152\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-27\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-21\",\"caseNumber\":\"GA024948\",\"caseId\":\"4034944027739185\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-02\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"储能一体化箱变\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-23\",\"caseNumber\":\"GA024982\",\"caseId\":\"4034943641880667\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-02\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-19\",\"caseNumber\":\"GA024920\",\"caseId\":\"4034944027920465\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-04\",\"caseNumber\":\"GA024643\",\"caseId\":\"4034944027739180\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-13\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-17\",\"caseNumber\":\"GA024880\",\"caseId\":\"4034944030918752\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-08\",\"caseNumber\":\"GA024699\",\"caseId\":\"4034944030918754\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-04\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":1,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-02\",\"caseNumber\":\"GA024608\",\"caseId\":\"4034944030525532\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-09-26\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}},{\"cwoMonTime\":[],\"inOrderNum\":9,\"casMonTime\":{\"readlyTime\":{\"zero\":false,\"value\":480},\"createdDate\":\"2025-09-10\",\"caseNumber\":\"GA024748\",\"caseId\":\"4034944030753840\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-08\",\"workTime\":{\"zero\":false,\"value\":-240},\"productType\":\"光伏电站整体\",\"status\":\"已评估\"}},{\"cwoMonTime\":[],\"inOrderNum\":4,\"casMonTime\":{\"readlyTime\":{\"zero\":true,\"value\":0},\"createdDate\":\"2025-09-22\",\"caseNumber\":\"GA024959\",\"caseId\":\"4034944030639134\",\"totalWorkTimeTotal\":{\"zero\":false,\"value\":240},\"expectFinishDate\":\"2025-10-03\",\"workTime\":{\"zero\":false,\"value\":240},\"productType\":\"成套开关设备\",\"status\":\"已关闭\"}}]}";
        String s = new MainApp().SaveCase(JSONObject.parseObject(json));
    }
    static class InMemoryCaseMonTimeRepository implements CaseWorkTimeController.CaseMonTimeRepository {
        @Override public java.util.List<CaseWorkTimeController.CaseMonTime> findCaseMonTimesByCaseIdsForMonth(java.util.Set<String> caseIds, int year, int month) { return java.util.Collections.emptyList(); }
        @Override public void upsert(java.util.List<CaseWorkTimeController.CaseMonTime> items) { /* no-op */ }
    }

    static class InMemoryCaseWorkOrderRepository implements CaseWorkTimeController.CaseWorkOrderRepository {
        @Override public void upsert(java.util.List<CaseWorkTimeController.CaseWorkOrder> items) { /* no-op */ }
    }
}