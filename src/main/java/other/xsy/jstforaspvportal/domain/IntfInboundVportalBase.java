package other.xsy.jstforaspvportal.domain;

import java.util.Map;

/**
 * 提供Vportal外部调用的基础处理类，所有的接口类都应该实现此方法
 * 
 * @author chenxi
 * @version 1.0
 */
public abstract class IntfInboundVportalBase {
    
    protected String debugLog = "";
    
    /**
     * 执行方法
     */
    public abstract IntfInboundVportalResult execute(IntfInboundVportalParam param);
    
    /**
     * 获取检查映射
     */
    public abstract Map<String, String> getCheckMap(IntfInboundVportalParam param);
    
    /**
     * 记录日志
     */
    public void recordLog(String log) {
        debugLog = debugLog + log + "\r\n";
    }
    
    public String getDebugLog() {
        return debugLog;
    }
}