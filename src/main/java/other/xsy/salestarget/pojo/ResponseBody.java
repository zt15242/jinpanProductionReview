package other.xsy.salestarget.pojo;

import com.alibaba.fastjson.annotation.JSONField;

import java.io.Serializable;

/**
 * @author CaiShuLian 2025-09-25 14:25
 * @since 接口返回的 数据
 */
public class ResponseBody implements Serializable {

    @JSONField(name = "code")
    private String code;
    @JSONField(name = "msg")
    private String msg;
    @JSONField(name = "errorInfo")
    private String errorInfo;
    @JSONField(name = "data")
    private Object data;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(String errorInfo) {
        this.errorInfo = errorInfo;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
