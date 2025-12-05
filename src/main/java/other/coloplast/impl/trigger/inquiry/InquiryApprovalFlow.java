package other.coloplast.impl.trigger.inquiry;

import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.creekflow.approvalevent.ApprovalEvent;
import com.rkhd.platform.sdk.creekflow.approvalevent.ApprovalEventRequest;
import com.rkhd.platform.sdk.creekflow.approvalevent.ApprovalEventResponse;
import com.rkhd.platform.sdk.creekflow.autoflowevent.AutoFlowEvent;
import com.rkhd.platform.sdk.creekflow.autoflowevent.AutoFlowEventRequest;
import com.rkhd.platform.sdk.creekflow.autoflowevent.AutoFlowEventResponse;
import com.rkhd.platform.sdk.data.model.Inquiry__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.ScriptBusinessException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import com.rkhd.platform.sdk.model.OperateResult;
import com.rkhd.platform.sdk.service.XObjectService;
import other.coloplast.impl.common.CommoninterfaceUtil;
import other.coloplast.impl.common.XMLParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InquiryApprovalFlow implements ApprovalEvent {
    private static Logger logger = LoggerFactory.getLogger();

    @Override
    public ApprovalEventResponse execute(ApprovalEventRequest approvalEventRequest) throws ScriptBusinessException {
        ApprovalEventResponse response = new ApprovalEventResponse();

        Long inquiryId = approvalEventRequest.getDataId();                 //流里面的数据
        List<String> idS = new ArrayList<>();
        Set<Long> accIdS=new HashSet<>();
        List<Long> id = new ArrayList<Long>();
        id.add(inquiryId);
        try {
            List<Inquiry__c> inquiryList = XObjectService.instance().getByIds("inquiry__c",id, true);

            idS.add(String.valueOf(inquiryId));
            accIdS.add(inquiryList.get(0).getAccount__c());

            if(!accIdS.isEmpty()){
                List<String> accIdStrings = new ArrayList<>();
                for(Long accId : accIdS){
                    accIdStrings.add(String.valueOf(accId));
                }
                CommoninterfaceUtil.sendInterfaceRequest( "Account", accIdStrings,"outbound");//Inquiry /SupplierToSAP
            }

            //询价单标识勾选后，同步至智能平台
            if(!idS.isEmpty() && inquiryList.get(0).getIMS_Code__c()==null){
                System.out.println("\n=== 测试接口请求 ===");
                String implResponse = CommoninterfaceUtil.sendInterfaceRequest( "Inquiry", idS,"outbound");//Inquiry /SupplierToSAP
                logger.info("implResponse===" + implResponse);
                implResponse = XMLParser.cleanXmlString(implResponse);
                implResponse.replaceAll("\\\\\\\\r","");
                implResponse.replaceAll("\\\\\\\\n","");
                implResponse.replaceAll("\\\\\\\\t","");
                implResponse = XMLParser.processXmlString(implResponse);
                String s1 = XMLParser.xmlToJson(implResponse);  // 使用当前类的xmlToJson方法
                JSONObject json = JSONObject.parseObject(s1);

                //xml转JSONObject  IMScode赋值
                logger.info("json===" + JSONObject.toJSONString(json));
                logger.info("json===" + json.getJSONObject("response").getJSONObject("fields").getString("msg"));
                if(json.getJSONObject("response").getJSONObject("fields").getString("msg").equals("操作成功")){
                    JSONObject responese = json.getJSONObject("response");
                    String imsCode = responese.getJSONObject("fields").getString("imsCode");
                    logger.info("imsCode===" + imsCode);
                    Inquiry__c updateInq = new Inquiry__c();
                    updateInq.setId(inquiryId);
                    updateInq.setIMS_Code__c(imsCode);
                    OperateResult uresult = XObjectService.instance().update(updateInq,true);
                    logger.info("updateInq" + JSONObject.toJSONString(updateInq));
                    if(uresult.getSuccess()){
                        response.setSuccess(true);
                        response.setMsg("流执行成功");
                    }else{
                        logger.error("同步失败===" + uresult.getErrorMessage());
                        response.setSuccess(false);
                        response.setMsg("同步失败");
                    }
                }
            }

        } catch (ApiEntityServiceException e) {
            throw new RuntimeException(e);
        }

        return response;
    }
}
