package other.xsy.jstforaspvportal.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.api.annotations.*;
import com.rkhd.platform.sdk.data.model.Event_Log__c;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.exception.XsyHttpException;
import com.rkhd.platform.sdk.http.RkhdHttpClient;
import com.rkhd.platform.sdk.http.RkhdHttpData;
import com.rkhd.platform.sdk.http.handler.ResponseBodyHandlers;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import other.xsy.jstforaspvportal.domain.IntfInboundVportalResult;
import other.xsy.jstforaspvportal.domain.VportalRespone;
import other.xsy.jstforaspvportal.util.CommoninterfaceUtil;
import other.xsy.jstforaspvportal.util.XMLParser;

import javax.xml.crypto.dsig.XMLObject;
import java.io.IOException;

/**
 * @author 武于伦
 * @version 1.0
 * @email 2717718875@qq.com
 * @data 2025/8/25 11:51
 */
@RestApi(baseUrl = "/service/apexrest")
public class JSTForSAPVportal {
    //@RestMapping(value = "/test/api/xml", method = RequestMethod.POST,responseMediaType = MediaType.TEXT_XML)
    //    public String testApiXml(@RestQueryParam(name = "xmlBody")String xmlBody) {}
    private static final Logger logger = LoggerFactory.getLogger();
    @RestMapping(value = "/JSTForSAPVportal",method = RequestMethod.POST,responseMediaType= MediaType.TEXT_XML)
    public String JSTForSAPVportal(@RestQueryParam(name = "xmlBody") String xmlBody) throws ApiEntityServiceException {
        String xml = xmlBody;
        logger.info(xmlBody);
        xmlBody = XMLParser.cleanXmlString(xmlBody);
        xmlBody.replaceAll("\\\\\\\\r","");
        xmlBody.replaceAll("\\\\\\\\n","");
        xmlBody.replaceAll("\\\\\\\\t","");
        xmlBody.replaceAll("\\\\&quot;","“");
        xmlBody.replaceAll("\\\\", "\\\\\\\\");

        xmlBody = XMLParser.processXmlString(xmlBody);
        logger.info(xmlBody);
        String s1 = XMLParser.xmlToJson(xmlBody);  // 使用当前类的xmlToJson方法
        JSONObject jsonObject = JSON.parseObject(s1);
        String trancode = jsonObject.getJSONObject("request").getJSONObject("head").getString("trancode");
        CommoninterfaceUtil.InterfaceConfig config = CommoninterfaceUtil.getInterfaceConfigFromSXY(trancode);
        Event_Log__c eventLog__c = new Event_Log__c();
        eventLog__c.setInterface_Config__c(config.id);
        eventLog__c.setBody_Content__c(xml);
        eventLog__c.setHeader_Content__c(config.interfaceActionUrl);
        eventLog__c.setCall_Time__c(System.currentTimeMillis());
        Long logId = CommoninterfaceUtil.insertEventLog(eventLog__c);
        String responseStr = null;
        try {
            // 获取request对象，包含fields和tables
            JSONObject request = jsonObject.getJSONObject("request");
            // 使用新的方法将tables数据合并到fields中
            JSONObject mergedFields = XMLParser.mergeTablesToFields(request);
            // 在映射前进行递归预处理：把可能是JSON的字符串解析为真正的JSON
            mergedFields = (JSONObject) XMLParser.preprocessPotentialJson(mergedFields);
            logger.info("合并后的fields数据:"+mergedFields.toJSONString());
            // 使用合并后的fields进行映射
            JSONObject mappingJson = CommoninterfaceUtil.autoMapping(trancode, "inbound", mergedFields);
            logger.info("最终映射结果:"+mappingJson.toJSONString());
            RkhdHttpClient client = RkhdHttpClient.instance();
            RkhdHttpData data = new RkhdHttpData();
            data.setBody(mappingJson.toJSONString());
            data.setCall_type("POST");
            data.setCallString("/rest/data/v2.0/scripts/api/service/apexrest/"+trancode);
            responseStr = client.execute(data, ResponseBodyHandlers.ofString());
            Event_Log__c updateLog = new Event_Log__c();
            updateLog.setId(logId);
            updateLog.setResult__c(1);
            updateLog.setReturn_Result__c(responseStr);
            CommoninterfaceUtil.updateEventLog(updateLog);
        } catch (Exception e) {
            Event_Log__c updateLog = new Event_Log__c();
            updateLog.setId(logId);
            updateLog.setError_Content__c(e.getMessage());
            updateLog.setResult__c(2);
//            throw new RuntimeException(e);
            // 获取request对象，包含fields和tables
            JSONObject request = jsonObject.getJSONObject("request");
            // 使用新的方法将tables数据合并到fields中
            JSONObject mergedFields = XMLParser.mergeTablesToFields(request);
            IntfInboundVportalResult iResult = new IntfInboundVportalResult();
            iResult.setVRespone(new VportalRespone());
            iResult.getVRespone().setHeads(mergedFields.getJSONObject("head"));
            iResult.setCode("BLUEFORCE_CUSTOM_INBOUND_EXCEPTION-100");
            iResult.setMessage(e.getMessage());
            iResult.getVRespone().getHeads().put("code", "2");
            iResult.getVRespone().getFields().put("msg", e.getMessage());
            updateLog.setReturn_Result__c(iResult.getVRespone().transferString());
            CommoninterfaceUtil.updateEventLog(updateLog);
            return iResult.getVRespone().transferString();
        }
        return responseStr;
    }

    public static void main(String[] args) {
        String xml2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><request><head><trancode>SAPERPOrder</trancode><code>0</code><reqserno>1FF9B295F4A21FE0A6CE1B31A021DEFD</reqserno><asynreq>0</asynreq><asyncode>0</asyncode><vsid>302</vsid><token>Authorization：OAuth e083fd29dc95901fa3ee9d70391fa49ad4f8721fddf925136b50dca752a8bc4d.MzkwMDMyMjY2NjA3MjE3Mw==0</token><EXEC>CALL-EXEC</EXEC></head><fields><jsondata><![CDATA[[{\"ORDER__C\":{\"VBELN\":\"2020100722\",\"AUART\":\"ZOR1\",\"ERDAT\":\"2020-06-01\",\"BSTDK\":\"2020-06-01\",\"KUNNR\":\"1000561\",\"VKBUR\":\"1165\",\"VKGRP\":\"128\",\"BSTKD\":\"\",\"BSTKD_E\":\"4200528461\",\"QRSZJE\":631061.50,\"QRSZJE2\":558461.50,\"QRSZJE3\":631061.50,\"QRSZJE4\":558461.50,\"WAERK\":\"CNY\",\"HWAER\":\"CNY\",\"VTWEG\":\"40\",\"EDATU\":\"2020-07-03\",\"QBJH\":\"全部交货\",\"IHREZ\":\"\",\"CRMXM\":\"\\\\\",\"CRMHT\":\"\\\\\",\"JJPC\":\"否\",\"KVGR5\":\"N\",\"HTQRS\":\"QRS-20200226\",\"KVGR1\":\"02\",\"LEGAL_ENTY\":\"Z5\",\"KVGR3\":\"01\",\"KUKLA\":\"Z2\",\"CREDIT_GROUP\":\"0001\",\"KVGR4\":\"合同原件已收到\",\"ZTERM\":\"D090\",\"TEXTT\":\"90天之内 到期净值\",\"TEXTZ\":\"到货款090天\",\"TEXTB\":\"5年\",\"LEIB\":\"内销C类\",\"YFHJE\":631061.50,\"ZTJE\":0,\"WFHJE\":0,\"YFHJB\":631061.50,\"ZTJEB\":0,\"WFHJB\":0,\"YKPJE\":126212.30,\"WKPJE\":504849.20,\"YSF2\":0,\"ZBFWF\":0,\"SJZZF\":0,\"WWJGF\":0,\"XJKCF\":0,\"KHKCF\":0,\"SYJCF\":0,\"JSFWF\":19018.36,\"SHFY\":0,\"AZF1\":0,\"BILLED\":631061.50,\"BILLED_B\":631061.50,\"UPDKZ\":\"\",\"VKORG\":\"1000\",\"ORDERITEM__C\":[{\"POSNR\":20,\"VBELN\":\"2020100722\",\"SPART\":\"41\",\"BEZEI\":\"002\",\"MATNR\":\"220101001643\",\"ARKTX\":\"非钢带箱 1617-1\",\"WRKST\":\"0\",\"GUIGE\":1000,\"ZIEME\":\"EA\",\"KWMENG\":50.000,\"STRGR\":\"\",\"ABGRU\":\"\",\"ABGRU_T\":\"\",\"WAERS\":\"\",\"WRBTR\":0,\"DMBTR\":0,\"WRBTR2\":0,\"DMBTR2\":0,\"DLVQTY_BU\":50.000,\"OCDQTY_BU\":0,\"UPDKZ\":\"\",\"ORDERPLANITEM__C\":[{\"ETENR\":1,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-15\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":3,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-25\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":4,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-17\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":7,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-25\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":8,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-30\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":9,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-29\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"}]},{\"POSNR\":10,\"VBELN\":\"2020100722\",\"SPART\":\"41\",\"BEZEI\":\"002\",\"MATNR\":\"3401616301\",\"ARKTX\":\"VPI配变 SGL-120/0.69 1616 YYN0 E 4% Z01\",\"WRKST\":\"SGL-120/0.69\",\"GUIGE\":120,\"ZIEME\":\"EA\",\"KWMENG\":50.000,\"STRGR\":\"\",\"ABGRU\":\"\",\"ABGRU_T\":\"\",\"WAERS\":\"CNY\",\"WRBTR\":631061.50,\"DMBTR\":631061.50,\"WRBTR2\":558461.50,\"DMBTR2\":558461.50,\"DLVQTY_BU\":50.000,\"OCDQTY_BU\":0,\"UPDKZ\":\"\",\"ORDERPLANITEM__C\":[{\"ETENR\":1,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-03\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":2,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-15\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":3,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-05\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":4,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-17\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"},{\"ETENR\":5,\"ETTYP\":\"ZZ\",\"JHHZD\":\"\",\"EDATU\":\"2020-07-30\",\"DGLTS\":\"0000-00-00\",\"RESWK\":\"3000\",\"STATUS\":\"已核发排产需求\"}]}],\"WFHJE2\":0,\"WFHJB2\":0}}]]]></jsondata></fields><tables></tables></request>";
        try {
            new JSTForSAPVportal().JSTForSAPVportal(xml2);
        } catch (ApiEntityServiceException e) {
//            throw new RuntimeException(e);
        }
    }
}
