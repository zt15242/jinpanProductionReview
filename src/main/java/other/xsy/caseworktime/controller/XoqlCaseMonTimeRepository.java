package other.xsy.caseworktime.controller;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.XoqlService;

public class XoqlCaseMonTimeRepository implements CaseWorkTimeController.CaseMonTimeRepository {

    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.of("+8");

    @Override
    public List<CaseWorkTimeController.CaseMonTime> findCaseMonTimesByCaseIdsForMonth(Set<String> caseIds, int year, int month) throws ApiEntityServiceException {
        if (caseIds == null || caseIds.isEmpty()) return Collections.emptyList();

        // 当月起止（东八区）转毫秒
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999_999_999);
        long start = monthStart.toInstant(ZONE_OFFSET).toEpochMilli();
        long end = monthEnd.toInstant(ZONE_OFFSET).toEpochMilli();

        // 注意：使用 createdDateNew__c 作为“创建日期”过滤字段
        String inClause = buildInClause(caseIds);

        String xoql =
            "SELECT " +
            " id,case__c,case__c.totalWorkTimeTota__c,case__c.readlyTime__c,case__c.standWorkTime__c,case__c.changeWorkTime__c," +
            " status__c,caseNumber__c,expectFinishDate__c,readlyTime__c,createdDateNew__c,productType__c,closedDate__c," +
            " totalWorkTimeTota__c,workTime__c,createdAt," +
            " (SELECT id,workOrderNumber__c,work_RecordType__c,workOrder__c,workTime__c,readlyTime2__c,alredy_Time__c,dealTime__c " +
            "  FROM case_WorkOrder__c WHERE caseMonTime__c = caseMonTime__c.id) Case_WorkOrder__r " +
            "FROM caseMonTime__c " +
            "WHERE case__c IN " + inClause + " AND createdDateNew__c>=" + start + " AND createdDateNew__c<" + end;

        QueryResult<JSONObject> qr = XoqlService.instance().query(xoql, true,true);
        List<CaseWorkTimeController.CaseMonTime> out = new ArrayList<>();
        if (qr == null || qr.getRecords() == null) return out;

        for (JSONObject row : qr.getRecords()) {
            CaseWorkTimeController.CaseMonTime cmt = new CaseWorkTimeController.CaseMonTime();
            cmt.id = getString(row, "id");
            cmt.caseId = getString(row, "case__c");
            cmt.workTime = toDecimal(row.get("workTime__c"));
            cmt.readlyTime = toDecimal(row.get("readlyTime__c"));
            cmt.totalWorkTimeTotal = toDecimal(row.get("totalWorkTimeTota__c"));
            cmt.status = getString(row, "status__c");
            cmt.caseNumber = getString(row, "caseNumber__c");
            cmt.expectFinishDate = toLocalDate(row.get("expectFinishDate__c"));
            cmt.createdDate = toLocalDate(row.get("createdDateNew__c")); // 关键：使用 createdDateNew__c
            cmt.productType = getString(row, "productType__c");
            cmt.closedDate = toLocalDate(row.get("closedDate__c"));

            // 子表 case_WorkOrder__c
            JSONArray caseWorkOrders = row.getJSONArray("Case_WorkOrder__r");
            if (caseWorkOrders != null && !caseWorkOrders.isEmpty()) {
                cmt.caseWorkOrders = new ArrayList<>(caseWorkOrders.size());
                for (int i = 0; i < caseWorkOrders.size(); i++) {
                    JSONObject w = caseWorkOrders.getJSONObject(i);
                    CaseWorkTimeController.CaseWorkOrder cwo = new CaseWorkTimeController.CaseWorkOrder();
                    cwo.id = getString(w, "id");
                    cwo.workOrderId = getString(w, "workOrder__c");
                    cwo.workOrderNumber = getString(w, "workOrderNumber__c");
                    cwo.workRecordType = getString(w, "work_RecordType__c");
                    cwo.totalTime = toDecimal(w.get("workTime__c"));
                    cwo.readlyTime = toDecimal(w.get("readlyTime2__c")); // 字段名与个案上不同
                    cwo.alreadyTime = toDecimal(w.get("alredy_Time__c"));
                    cwo.dealTime = toDecimal(w.get("dealTime__c"));
                    cwo.caseMonTimeId = cmt.id;
                    cmt.caseWorkOrders.add(cwo);
                }
            } else {
                cmt.caseWorkOrders = Collections.emptyList();
            }
            out.add(cmt);
        }
        return out;
    }

    @Override
    public void upsert(List<CaseWorkTimeController.CaseMonTime> items) {
        // 这里按你的平台批量 upsert 即可；示例先留空
        // XoqlService.instance().upsert("caseMonTime__c", itemsMappedToJsonArray);
    }

    // ------- helpers -------

    private static String buildInClause(Set<String> ids) {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (String id : ids) {
            if (id == null) continue;
            if (!first) sb.append(", ");
            sb.append("'").append(id.replace("'", "''")).append("'");
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    private static String getString(JSONObject o, String key) {
        Object v = o.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static CaseWorkTimeController.Decimal toDecimal(Object v) {
        if (v == null) return new CaseWorkTimeController.Decimal(0);
        if (v instanceof CaseWorkTimeController.Decimal) return (CaseWorkTimeController.Decimal) v;
        if (v instanceof BigDecimal) return new CaseWorkTimeController.Decimal((BigDecimal) v);
        if (v instanceof Number) return new CaseWorkTimeController.Decimal(new BigDecimal(v.toString()));
        try { return new CaseWorkTimeController.Decimal(new BigDecimal(v.toString())); }
        catch (Exception e) { return new CaseWorkTimeController.Decimal(0); }
    }

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
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (Exception e) {
            try { return LocalDate.parse(s); } catch (Exception e2) { return null; }
        }
    }
}