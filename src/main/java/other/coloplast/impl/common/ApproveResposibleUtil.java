package other.coloplast.impl.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.data.model.Approve_Resposible_c__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.model.QueryResult;
import com.rkhd.platform.sdk.service.XObjectService;
import com.rkhd.platform.sdk.service.XoqlService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author CaiShuLian 2025-09-28 12:59
 * @since 审批人员对应业务
 */
public class ApproveResposibleUtil {

    private static final Logger log = LoggerFactory.getLogger();

    private static ApproveResposibleUtil singleton = new ApproveResposibleUtil();
    public static ApproveResposibleUtil instance() {
        return singleton;
    }

    /**
     * 设置审批人员
     * @param xObjects 数据集合
     * @param bPApproverFiled 事业部的api名称
     * @param salesApproverField 营销中心的api名称
     * @param productApproverField 产品组的api名称
     */
    public void approveResposible(List<com.rkhd.platform.sdk.model.XObject> xObjects, String bPApproverFiled, String salesApproverField, String productApproverField) {
        log.info("设置审批人员的入参,xObjects:" + JSON.toJSONString(xObjects));
        if(xObjects == null || xObjects.isEmpty()){
            log.error("设置审批人员失败,入参xObjects为空");
            return;
        }
        //查询所有审批人员对应表
        try {
            String approveResposibleSql = "select id,BP_Approver_c__c,sales_Approver_c__c,quoter_Approver_c__c,product_Approver_c__c,contract_Approver_c__c from approve_Resposible_c__c";
            QueryResult<JSONObject> queryApproveResposible = XoqlService.instance().query(approveResposibleSql,true,true);
            List<Approve_Resposible_c__c> approveResposibleList = queryApproveResposible.getRecords().stream().map(json -> json.toJavaObject(Approve_Resposible_c__c.class)).collect(Collectors.toList());
            if(approveResposibleList.isEmpty()){
                log.error("审批人员对应表为空");
                return;
            }
            log.info("查询所有审批人员对应表成功:"+ JSON.toJSONString(approveResposibleList));
            for (com.rkhd.platform.sdk.model.XObject xObject : xObjects) {
                Long quoterApproverId = null;
                Long contractApproverId = null;
                for (Approve_Resposible_c__c approveResposible : approveResposibleList) {
                    //事业部
                    boolean isBPApproved = approveResposible.getBP_Approver_c__c() == null ||
                            approveResposible.getBP_Approver_c__c().equals(xObject.getAttribute(bPApproverFiled));
                    //营销中心
                    boolean isSalesApproved = approveResposible.getSales_Approver_c__c() == null ||
                            approveResposible.getSales_Approver_c__c().equals(xObject.getAttribute(salesApproverField));
                    //产品组
                    boolean isProductApproved = approveResposible.getProduct_Approver_c__c() == null || productApproverField == null || productApproverField.isEmpty() ||
                            approveResposible.getProduct_Approver_c__c().equals(xObject.getAttribute(productApproverField));

                    // 当所有条件满足时设置审批人员ID
                    if (isBPApproved && isSalesApproved && isProductApproved) {
                        log.info("满足的规则匹配:Id:"+approveResposible.getId()+",BPApprover:"+approveResposible.getBP_Approver_c__c()+
                                ",salesApprover:"+approveResposible.getSales_Approver_c__c()+",productApprover:"+ approveResposible.getProduct_Approver_c__c()+
                                ",quoterApproverId:"+approveResposible.getQuoter_Approver_c__c()+",contractApproverId:"+approveResposible.getContract_Approver_c__c());
                        if(approveResposible.getQuoter_Approver_c__c() != null){
                            quoterApproverId = approveResposible.getQuoter_Approver_c__c();
                        }
                        if(approveResposible.getContract_Approver_c__c() != null){
                            contractApproverId = approveResposible.getContract_Approver_c__c();
                        }
                    }
                }
                //更新业务表审批人员
                if(quoterApproverId != null || contractApproverId != null){
                    XObject upObject = new XObject(xObject.getApiKey());
                    if(xObject.getId() == null){
                        log.error("更新业务表审批人员失败,ID为空,xObject:"+ JSON.toJSONString(xObject));
                        continue;
                    }
                    upObject.setId(xObject.getId());
                    if(quoterApproverId != null){
                        upObject.setAttribute("quoter_Approver__c",quoterApproverId);
                    }
                    if(contractApproverId != null){
                        upObject.setAttribute("contract_Approver__c",contractApproverId);
                    }
                    OperateResult operateResult = XObjectService.instance().update(upObject,true);
                    if(operateResult.getSuccess()){
                        log.info("更新业务表审批人员apiKey:"+upObject.getApiKey()+",ID:"+upObject.getId()+"成功!");
                    }else{
                        log.error("更新业务表审批人员apiKey:"+upObject.getApiKey()+",ID:"+upObject.getId()+"失败!,原因："+operateResult.getErrorMessage());
                    }
                }else{
                    log.error("没有匹配的审批人员,xObject:"+ JSON.toJSONString(xObject));
                }
            }
        }catch (Exception e){
            log.error("设置审批人员失败",e);
        }
    }

    /**
     * 自定义XObject，继承父类 XObject，添加自定义方法
     */
    class XObject extends com.rkhd.platform.sdk.model.XObject{
        public XObject(String apiKey) {
            super(apiKey);
        }
    }

    public static void main(String[] args) throws ApiEntityServiceException {
        //项目投标
        /*String testSql = "select id,department__c,applicant_Department__c,quoter_Approver__c,product_Group__c,contract_Approver__c from bid__c where id in (4002267225015308,4002276305339425,4002286620558338)";
        QueryResult<com.rkhd.platform.sdk.model.XObject> bidQueryResult = XObjectService.instance().query(testSql,true,true);
        List<com.rkhd.platform.sdk.model.XObject> bidList = bidQueryResult.getRecords();
        ApproveResposibleUtil.instance().approveResposible(bidList,"department__c","applicant_Department__c","product_Group__c");*/

        //发货通知单
        String testSql2 = "select id,department__c,sales_Office__c,contract_Approver__c,quoter_Approver__c from Production_Plan__c where id in (4002413047759889)";
        QueryResult<com.rkhd.platform.sdk.model.XObject> daQueryResult = XObjectService.instance().query(testSql2,true,true);
        List<com.rkhd.platform.sdk.model.XObject> daList = daQueryResult.getRecords();
        ApproveResposibleUtil.instance().approveResposible(daList,"department__c","sales_Office__c",null);
    }
}
