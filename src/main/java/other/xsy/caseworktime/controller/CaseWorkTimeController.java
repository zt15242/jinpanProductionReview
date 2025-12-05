package other.xsy.caseworktime.controller;

import com.rkhd.platform.sdk.exception.ApiEntityServiceException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
/**
 * Java 等价版本的 CaseWorkTimeController
 * - 替换了 SOQL/DML 为 Repository 接口
 * - apex 特有字段与对象以 POJO 表示
 * - 日期逻辑使用 java.time
 */
public class CaseWorkTimeController {

    // region Public APIs

    public static PickClass getCase(CaseWorkTimeContext context) throws ApiEntityServiceException {
        // 原 APEX：取当前用户、当前年月
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int mon = today.getMonthValue();
        YearMonth currentYm = YearMonth.of(year, mon);

        // 原 APEX：查询历史未关闭 + 当月关闭 且 InOrderNum__c > 0 的 Case
        List<CaseRecord> caseList = context.caseRepository()
            .findCasesForWorkTime(year, mon);

        PickClass pickClass = new PickClass();
        pickClass.casMonTList = new ArrayList<>();
        pickClass.casMonTList2 = new ArrayList<>();

        if (caseList == null || caseList.isEmpty()) {
            return pickClass;
        }

        Set<String> caseIdSet = caseList.stream()
            .map(c -> c.id)
            .collect(Collectors.toSet());
        System.out.println(caseIdSet);
        System.out.println(year+"--"+mon);
        // 原 APEX：查当月 CaseMonTime__c（含子表 Case_WorkOrder__c）
        List<CaseMonTime> caseMonTimes = context.caseMonTimeRepository()
            .findCaseMonTimesByCaseIdsForMonth(caseIdSet, year, mon);

        Map<String, CaseMonTime> caseIdToMonTime = new HashMap<>();
        Map<String, CaseWorkOrder> workOrderIdToCaseWorkOrder = new HashMap<>();

        if (caseMonTimes != null) {
            for (CaseMonTime cmt : caseMonTimes) {
                caseIdToMonTime.put(cmt.caseId, cmt);
                if (cmt.caseWorkOrders != null) {
                    for (CaseWorkOrder cwo : cmt.caseWorkOrders) {
                        if (cwo.workOrderId != null) {
                            workOrderIdToCaseWorkOrder.put(cwo.workOrderId, cwo);
                        }
                    }
                }
            }
        }

        for (CaseRecord ca : caseList) {
            InnerClass inner = new InnerClass();
            inner.casMonTime = new CaseMonTime();
            inner.cwoMonTime = new ArrayList<>();
            inner.inOrderNum = safeInt(ca.inOrderNum);

            if (caseIdToMonTime.containsKey(ca.id)) {
                // 已有当月 CaseMonTime
                CaseMonTime cti = caseIdToMonTime.get(ca.id);
                inner.casMonTime = cti;

                // 计算个案工时
                cti.totalWorkTimeTotal = safeDec(ca.standWorkTime).add(safeDec(ca.changeWorkTime));
                cti.readlyTime = safeDec(ca.readlyTime);
                cti.workTime = cti.totalWorkTimeTotal.subtract(cti.readlyTime);

                // 合计工时 = 个案工时 + 子单处理工时
                Decimal totalTime = new Decimal(cti.workTime.value);

                if (ca.workOrders != null && !ca.workOrders.isEmpty()) {
                    for (WorkOrder wo : ca.workOrders) {
                        if (workOrderIdToCaseWorkOrder.containsKey(wo.id)) {
                            CaseWorkOrder cwo = workOrderIdToCaseWorkOrder.get(wo.id);

                            cwo.totalTime = safeDec(wo.workTime);
                            cwo.readlyTime = safeDec(wo.readlyTime);
                            cwo.dealTime = cwo.totalTime.subtract(cwo.readlyTime);

                            if (!inner.cwoMonTime.contains(cwo)) {
                                inner.cwoMonTime.add(cwo);
                            }
                            totalTime = totalTime.add(cwo.dealTime);
                        } else {
                            CaseWorkOrder cwo = new CaseWorkOrder();
                            cwo.workOrderId = wo.id;
                            cwo.status = wo.status;
                            cwo.totalTime = safeDec(wo.workTime);
                            cwo.readlyTime = safeDec(wo.readlyTime);
                            cwo.dealTime = cwo.totalTime.subtract(cwo.readlyTime);
                            cwo.caseMonTimeId = cti.id;
                            cwo.workOrderNumber = wo.workOrderNumber;
                            cwo.workRecordType = wo.recordTypeName;
                            cwo.alreadyTime = new Decimal(0);

                            inner.cwoMonTime.add(cwo);
                            totalTime = totalTime.add(cwo.dealTime);
                        }
                    }
                }

                if (cti.createdDate != null) {
                    YearMonth cmtYm = YearMonth.from(cti.createdDate);
                    if (cmtYm.equals(currentYm) && !totalTime.isZero()) {
                        pickClass.casMonTList.add(inner);
                    } else if (!cmtYm.equals(currentYm) && !totalTime.isZero()) {
                        pickClass.casMonTList2.add(inner);
                    }
                }
            } else {
                // 当月没有 CaseMonTime 时创建一个临时的
                CaseMonTime ct = new CaseMonTime();
                ct.caseId = ca.id;
                ct.status = ca.status;
                ct.caseNumber = ca.caseNumber;
                ct.expectFinishDate = ca.expectFinishDate;
                ct.createdDate = ca.createdDate;
                ct.productType = ca.productType;

                Decimal standPlusChange = safeDec(ca.standWorkTime).add(safeDec(ca.changeWorkTime));
                Decimal readly = safeDec(ca.readlyTime);

                ct.workTime = standPlusChange.subtract(readly);
                ct.readlyTime = readly;
                ct.totalWorkTimeTotal = standPlusChange;

                inner.casMonTime = ct;

                Decimal totalTime = new Decimal(ct.workTime.value);

                if (ca.workOrders != null && !ca.workOrders.isEmpty()) {
                    for (WorkOrder wo : ca.workOrders) {
                        CaseWorkOrder cwo = new CaseWorkOrder();
                        cwo.workOrderId = wo.id;
                        cwo.status = wo.status;
                        cwo.totalTime = safeDec(wo.workTime);
                        cwo.readlyTime = safeDec(wo.readlyTime);
                        cwo.dealTime = cwo.totalTime.subtract(cwo.readlyTime);
                        cwo.workOrderNumber = wo.workOrderNumber;
                        cwo.workRecordType = wo.recordTypeName;
                        cwo.alreadyTime = new Decimal(0);

                        inner.cwoMonTime.add(cwo);
                        totalTime = totalTime.add(cwo.dealTime);
                    }
                }

                if (ca.createdDate != null) {
                    YearMonth caYm = YearMonth.from(ca.createdDate);
                    if (caYm.equals(currentYm) && !totalTime.isZero()) {
                        pickClass.casMonTList.add(inner);
                    } else if (!caYm.equals(currentYm) && !totalTime.isZero()) {
                        pickClass.casMonTList2.add(inner);
                    }
                }
            }
        }

        return pickClass;
    }

    public static class PickClass {
        public List<InnerClass> casMonTList;
        public List<InnerClass> casMonTList2;
    }

    public static class InnerClass {
        public CaseMonTime casMonTime;
        public List<CaseWorkOrder> cwoMonTime;
        public Integer inOrderNum;
    }

    public static class CaseRecord {
        public String id;
        public Integer inOrderNum;
        public Decimal standWorkTime;
        public Decimal changeWorkTime;
        public LocalDate createdDate;
        public String caseNumber;
        public LocalDate expectFinishDate;
        public Decimal readlyTime;
        public String productType;
        public String status;
        public Decimal totalWorkTimeTotal;
        public List<WorkOrder> workOrders;
        public LocalDate closedDate;
    }

    public static class WorkOrder {
        public String id;
        public Decimal workTime;
        public Decimal readlyTime;
        public String status;
        public String recordTypeName;
        public String workOrderNumber;
    }

    public static class CaseMonTime {
        public String id;
        public String caseId;
        public Decimal workTime;
        public Decimal readlyTime;
        public Decimal totalWorkTimeTotal;
        public String status;
        public String caseNumber;
        public LocalDate expectFinishDate;
        public LocalDate createdDate;
        public String productType;
        public LocalDate closedDate;
        public List<CaseWorkOrder> caseWorkOrders;
    }

    public static class CaseWorkOrder {
        public String id;
        public String caseMonTimeId;
        public String workOrderId;
        public Decimal totalTime;
        public Decimal readlyTime;
        public Decimal dealTime;
        public String status;
        public String workOrderNumber;
        public String workRecordType;
        public Decimal alreadyTime;
    }

    // endregion

    // region Infra context & repos

    public interface CaseWorkTimeContext {
        CaseRepository caseRepository();
        CaseMonTimeRepository caseMonTimeRepository();
        CaseWorkOrderRepository caseWorkOrderRepository();
        TransactionManager transactionManager();
    }

    public interface CaseRepository {
        /**
         * 返回 (Status != 已关闭) 或 当月已关闭，且 InOrderNum__c > 0 的工单
         */
        List<CaseRecord> findCasesForWorkTime(int year, int month) throws ApiEntityServiceException;
    }

    public interface CaseMonTimeRepository {
        List<CaseMonTime> findCaseMonTimesByCaseIdsForMonth(Set<String> caseIds, int year, int month) throws ApiEntityServiceException;
        void upsert(List<CaseMonTime> items);
    }

    public interface CaseWorkOrderRepository {
        void upsert(List<CaseWorkOrder> items);
    }

    public interface TransactionManager {
        void runInTransaction(Runnable runnable);
    }

    // endregion

    // region Decimal helper (简易 BigDecimal 包装，支持空值安全运算)

    public static class Decimal {
        public final java.math.BigDecimal value;

        public Decimal(double v) {
            this.value = java.math.BigDecimal.valueOf(v);
        }

        public Decimal(java.math.BigDecimal v) {
            this.value = v == null ? java.math.BigDecimal.ZERO : v;
        }

        public Decimal add(Decimal other) {
            return new Decimal(this.value.add(other.value));
        }

        public Decimal subtract(Decimal other) {
            return new Decimal(this.value.subtract(other.value));
        }

        public boolean isZero() {
            return value.compareTo(java.math.BigDecimal.ZERO) == 0;
        }

        @Override
        public String toString() {
            return value.toPlainString();
        }
    }

    private static Decimal safeDec(Decimal d) {
        return d == null ? new Decimal(0) : d;
    }

    private static Integer safeInt(Integer i) {
        return i == null ? 0 : i;
    }

    // endregion
}