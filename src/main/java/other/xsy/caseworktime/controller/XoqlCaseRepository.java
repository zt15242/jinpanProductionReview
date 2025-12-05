package other.xsy.caseworktime.controller;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.model.MetadataModel;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.MetadataService;
import com.rkhd.platform.sdk.service.XoqlService;
import other.xsy.caseworktime.util.ObjectOptionValueRetrieval;

// 基于 XOQL 的实现
public class XoqlCaseRepository implements CaseWorkTimeController.CaseRepository {

    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.of("+8");

    @Override
    public List<CaseWorkTimeController.CaseRecord> findCasesForWorkTime(int year, int month) throws ApiEntityServiceException {
        // 计算当月起止（东八区）转毫秒
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999_999_999);
        long start = monthStart.toInstant(ZONE_OFFSET).toEpochMilli();
        long end = monthEnd.toInstant(ZONE_OFFSET).toEpochMilli();

        MetadataModel staff = MetadataService.instance().getBusiType("fieldJob", "defaultBusiType");
        Map<String, Map<String, String>> serviceCase = ObjectOptionValueRetrieval.getAllFieldOptions("serviceCase");
        Map<String, Map<String, String>> fieldJob2 = ObjectOptionValueRetrieval.getAllFieldOptions("fieldJob");
        String xoql =
                "SELECT " +
                "id,inOrderNum__c,standWorkTime__c,changeWorkTime__c,createdAt,caseNumber__c,createdDateNew__c," +
                "expectFinishDate__c,readlyTime__c,productType__c,caseStatus,totalWorkTimeTota__c,closedDate__c,completeTime,createdAt," +
                "(SELECT id,workTime__c,readlyTime__c,status,entityType,entityTypeName__c,workOrderNumber_new__c FROM fieldJob " +
                " WHERE entityType!=" + staff.getId() + " AND serviceCaseName=serviceCase.id) WorkOrders " +
                "FROM serviceCase " +
                "WHERE (caseStatus!=4 OR (caseStatus=4 AND closedDate__c>=" + start + " AND closedDate__c<" + end + ")) " +
                "AND inOrderNum__c>0";
        System.out.println(xoql);
        QueryResult<JSONObject> qr = XoqlService.instance().query(xoql, true, true);
        System.out.println(qr.getRecords());
        List<CaseWorkTimeController.CaseRecord> out = new ArrayList<>();
        if (qr == null || qr.getRecords() == null) return out;

        for (JSONObject row : qr.getRecords()) {
            CaseWorkTimeController.CaseRecord c = new CaseWorkTimeController.CaseRecord();
            c.id = getString(row, "id");
//            c.inOrderNum = getInteger(row, "inOrderNum__c");
            c.inOrderNum = row.getInteger("inOrderNum__c");
            c.standWorkTime = toDecimal(row.get("standWorkTime__c"));
            c.changeWorkTime = toDecimal(row.get("changeWorkTime__c"));
            c.createdDate = toLocalDate(row.get("createdDateNew__c"));
            c.caseNumber = getString(row, "caseNumber__c");
            c.expectFinishDate = toLocalDate(row.get("expectFinishDate__c"));
            c.readlyTime = toDecimal(row.get("readlyTime__c"));
            c.productType = serviceCase.get("productType__c").get(getString(row, "productType__c"));
            c.status = String.valueOf(serviceCase.get("caseStatus").get(row.get("caseStatus"))); // 数字状态转字符串保存
            c.totalWorkTimeTotal = toDecimal(row.get("totalWorkTimeTota__c"));
            c.closedDate = toLocalDate(row.get("closedDate__c"));

            // 子查询 WorkOrders
            JSONArray wos = row.getJSONArray("WorkOrders");
            if (wos != null && !wos.isEmpty()) {
                c.workOrders = new ArrayList<>(wos.size());
                for (int i = 0; i < wos.size(); i++) {
                    JSONObject w = wos.getJSONObject(i);
                    CaseWorkTimeController.WorkOrder wo = new CaseWorkTimeController.WorkOrder();
                    wo.id = getString(w, "id");
                    wo.workTime = toDecimal(w.get("workTime__c"));
                    wo.readlyTime = toDecimal(w.get("readlyTime__c"));
                    wo.status = String.valueOf(fieldJob2.get("status").get(w.get("status")));
                    wo.recordTypeName = String.valueOf(w.get("entityTypeName__c")); // 用 entityType 充当记录类型名称
                    wo.workOrderNumber = getString(w, "workOrderNumber_new__c");
                    c.workOrders.add(wo);
                }
            } else {
                c.workOrders = Collections.emptyList();
            }
            out.add(c);
        }
        return out;
    }

    // ---- helpers ----

    private static String getString(JSONObject o, String key) {
        Object v = o.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Integer getInteger(JSONObject o, String key) {
        Object v = o.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    // createdAt/expectFinishDate/closedDate__c 可能是毫秒时间戳或 ISO 字符串，均做兼容
    private static LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof Number) {
            long ms = ((Number) v).longValue();
            return Instant.ofEpochMilli(ms).atOffset(ZONE_OFFSET).toLocalDate();
        }
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            long ms = Long.parseLong(s);
            return Instant.ofEpochMilli(ms).atOffset(ZONE_OFFSET).toLocalDate();
        } catch (NumberFormatException ignore) {
        }
        try {
            // 让 java 解析 ISO 或常见格式
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (Exception e) {
            try { return LocalDate.parse(s); } catch (Exception e2) { return null; }
        }
    }

    private static CaseWorkTimeController.Decimal toDecimal(Object v) {
        if (v == null) return new CaseWorkTimeController.Decimal(0);
        if (v instanceof CaseWorkTimeController.Decimal) return (CaseWorkTimeController.Decimal) v;
        if (v instanceof BigDecimal) return new CaseWorkTimeController.Decimal((BigDecimal) v);
        if (v instanceof Number) return new CaseWorkTimeController.Decimal(new BigDecimal(v.toString()));
        try { return new CaseWorkTimeController.Decimal(new BigDecimal(v.toString())); }
        catch (Exception e) { return new CaseWorkTimeController.Decimal(0); }
    }
}