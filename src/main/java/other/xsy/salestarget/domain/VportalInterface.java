package other.xsy.salestarget.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vportal接口类
 * 
 * @author System
 * @version 1.0
 */
public abstract class VportalInterface {
    
    protected Map<String, Object> heads = new HashMap<>();
    protected Map<String, Object> fields = new HashMap<>();
    protected Map<String, VportalTable> tables = new HashMap<>();
    
    /**
     * 转换为XML字符串
     */
    public String transferString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(getBTab(getType()));
        
        sb.append(getBTab("head"));
        for (String k : heads.keySet()) {
            sb.append(getBTab(k)).append(getCData(String.valueOf(heads.get(k)))).append(getETab(k));
        }
        sb.append(getETab("head"));
        
        sb.append(getBTab("fields"));
        for (String k : fields.keySet()) {
            sb.append(getBTab(k)).append(getCData(String.valueOf(fields.get(k)))).append(getETab(k));
        }
        sb.append(getETab("fields"));
        
        sb.append(getBTab("tables"));
        for (String k : tables.keySet()) {
            sb.append(tables.get(k).transferString());
        }
        sb.append(getETab("tables"));
        sb.append(getETab(getType()));
        
        return sb.toString();
    }
    
    /**
     * 转换为清晰字符串（无CDATA）
     */
    public String toClearString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getBTab(getType()));
        
        sb.append(getBTab("head"));
        for (String k : heads.keySet()) {
            sb.append(getBTab(k)).append(String.valueOf(heads.get(k))).append(getETab(k));
        }
        sb.append(getETab("head"));
        
        sb.append(getBTab("fields"));
        for (String k : fields.keySet()) {
            sb.append(getBTab(k)).append(String.valueOf(fields.get(k))).append(getETab(k));
        }
        sb.append(getETab("fields"));
        
        sb.append(getBTab("tables"));
        for (String k : tables.keySet()) {
            sb.append(tables.get(k).toClearString());
        }
        sb.append(getETab("tables"));
        sb.append(getETab(getType()));
        
        return sb.toString();
    }
    
    /**
     * 获取类型
     */
    public abstract String getType();
    
    /**
     * 获取开始标签
     */
    protected String getBTab(String tabname) {
        return "<" + tabname + ">";
    }
    
    /**
     * 获取结束标签
     */
    protected String getETab(String tabname) {
        return "</" + tabname + ">";
    }

    public Map<String, Object> getHeads() {
        return heads;
    }

    public void setHeads(Map<String, Object> heads) {
        this.heads = heads;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public Map<String, VportalTable> getTables() {
        return tables;
    }

    public void setTables(Map<String, VportalTable> tables) {
        this.tables = tables;
    }

    /**
     * 获取CDATA包装
     */
    protected static String getCData(String v) {
        return "<![CDATA[" + v + "]]>";
    }
    
    /**
     * 将字段和表格数据整理成结果数据
     */
    public List<Map<String, Object>> parseResultData(Map<String, Object> fields, Map<String, VportalTable> tables) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        
        if (fields == null || fields.isEmpty()) {
            if (tables != null && tables.keySet().size() == 1) {
                for (String key : tables.keySet()) {
                    VportalTable tempVT = tables.get(key);
                    resultList.addAll(tempVT.parseToObjList());
                }
            }
        } else if (fields != null) {
            if (tables != null) {
                for (String key : tables.keySet()) {
                    VportalTable tempVT = tables.get(key);
                    fields.put(key, tempVT.parseToObjList());
                }
            }
            resultList.add(fields);
        }
        
        return resultList;
    }
}