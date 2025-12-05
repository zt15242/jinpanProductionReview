package other.xsy.salestarget.domain;

/**
 * Vportal接口入站结果类
 * 
 * @author System
 * @version 1.0
 */
public class IntfInboundVportalResult {
    
    private String code;
    private String message;
    private String msg;
    private String returnStr;
    private VportalRespone vRespone;
    
    // Getters and Setters
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getMsg() {
        return msg;
    }
    
    public void setMsg(String msg) {
        this.msg = msg;
    }
    
    public String getReturnStr() {
        return returnStr;
    }
    
    public void setReturnStr(String returnStr) {
        this.returnStr = returnStr;
    }
    
    public VportalRespone getVRespone() {
        return vRespone;
    }
    
    public void setVRespone(VportalRespone vRespone) {
        this.vRespone = vRespone;
    }
}