package other.xsy.jstforaspvportal.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.api.annotations.RequestMethod;
import com.rkhd.platform.sdk.api.annotations.RestApi;
import com.rkhd.platform.sdk.api.annotations.RestBeanParam;
import com.rkhd.platform.sdk.api.annotations.RestMapping;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import other.xsy.jstforaspvportal.util.CommoninterfaceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 武于伦
 * @version 1.0
 * @email 2717718875@qq.com
 * @data 2025/9/9 13:30
 */
@RestApi(baseUrl = "/service/apexrest")
public class InterfaceRepushService {
    private static final Logger logger = LoggerFactory.getLogger();
    private class returnParameters{
        public String interfaceName; // 接口名称
        public Integer code; // 200 成功 500 失败
        public String message; // 查询信息
        public String body;
    }
    /***
    * @Description: 重推基本接口，无返回信息写入数据的
    * @Param: [jsonObject]
    * @return: other.xsy.jstforaspvportal.service.InterfaceRepushService.returnParameters
    * @Author: 武于伦
    * @email: 2717718875@qq.com
    * @Date: 2025/11/4
    */
    @RestMapping(value = "rePushTheBasicInterface",method = RequestMethod.POST)
    public returnParameters rePush(@RestBeanParam(name = "json")JSONObject jsonObject){
        // idList  数据ID    interfaceName  接口名称
        returnParameters returnParameters = new returnParameters();
        JSONArray jsonArray = jsonObject.getJSONArray("idList");
        List<String> idList = new ArrayList<>();
        if(jsonArray != null){
            idList = jsonArray.toJavaList(String.class);
        }
        String interfaceName = jsonObject.getString("interfaceName");
        if (interfaceName == null || interfaceName.equals("")) {
            returnParameters.code = 500;
            returnParameters.message = "接口名称不能为空！";
            return returnParameters;
        }
        if (idList == null || idList.isEmpty()) {
            returnParameters.code = 500;
            returnParameters.message = "数据ID不能为空！";
            return returnParameters;
        }
        returnParameters.interfaceName = interfaceName;
        try {
            String string = CommoninterfaceUtil.sendInterfaceRequest(interfaceName, idList, "outbound");
            returnParameters.message = "重推成功";
            returnParameters.body = string;
            returnParameters.code = 200;
            return returnParameters;
        } catch (ApiEntityServiceException e) {
            returnParameters.code = 500;
            returnParameters.message = "重推失败："+e.getMessage();
            return returnParameters;
        }
    }
}
