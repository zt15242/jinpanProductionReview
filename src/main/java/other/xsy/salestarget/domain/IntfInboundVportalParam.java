package other.xsy.salestarget.domain;

import java.util.List;
import java.util.Map;

/**
 * Vportal接口入站参数类
 * 
 * @author Sera Liao
 * @version 1.0
 */
public class IntfInboundVportalParam {
    
    // 接口唯一名称
    private String theIntfName;
    // 目标对象列表
    private List<Object> dataList;
    // 详细对象映射
    private Map<String, List<List<Object>>> dataMap;
    // JSON字符串
    private String dataStr;
    // Vportal请求
    private VportalRequest vRequest;
    // 验证接收数据的结果
    private DataVerifyResult dataVerifyResult;
    
    /**
     * 数据验证结果内部类
     */
    public static class DataVerifyResult {
        private boolean isSuccess = true;
        private String massage = "";
        
        public boolean isSuccess() {
            return isSuccess;
        }
        
        public void setSuccess(boolean success) {
            isSuccess = success;
        }
        
        public String getMassage() {
            return massage;
        }
        
        public void setMassage(String massage) {
            this.massage = massage;
        }
    }
    
    // Getters and Setters
    public String getTheIntfName() {
        return theIntfName;
    }
    
    public void setTheIntfName(String theIntfName) {
        this.theIntfName = theIntfName;
    }
    
    public List<Object> getDataList() {
        return dataList;
    }
    
    public void setDataList(List<Object> dataList) {
        this.dataList = dataList;
    }
    
    public Map<String, List<List<Object>>> getDataMap() {
        return dataMap;
    }
    
    public void setDataMap(Map<String, List<List<Object>>> dataMap) {
        this.dataMap = dataMap;
    }
    
    public String getDataStr() {
        return dataStr;
    }
    
    public void setDataStr(String dataStr) {
        this.dataStr = dataStr;
    }
    
    public VportalRequest getVRequest() {
        return vRequest;
    }
    
    public void setVRequest(VportalRequest vRequest) {
        this.vRequest = vRequest;
    }
    
    public DataVerifyResult getDataVerifyResult() {
        return dataVerifyResult;
    }
    
    public void setDataVerifyResult(DataVerifyResult dataVerifyResult) {
        this.dataVerifyResult = dataVerifyResult;
    }
}