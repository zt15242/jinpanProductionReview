package other.xsy.salestarget.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vportal请求返回的table类
 * 
 * @author System
 * @version 1.0
 */
public class VportalTable {
    
    private List<String> thead = new ArrayList<>();
    private List<List<String>> tbody = new ArrayList<>();
    private String tablename;
    private String type = "table";
    
    /**
     * 构造函数
     */
    public VportalTable(String tabname) {
        this.tablename = tabname;
    }
    
    /**
     * 转换为XML字符串
     */
    public String transferString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getBTab("table tablename=\"" + this.tablename + "\" type=\"" + this.type + "\" "));
        
        sb.append(getBTab("thead"));
        for (String str : this.thead) {
            sb.append(getBTab("col")).append(getCData(str)).append(getETab("col"));
        }
        sb.append(getETab("thead"));
        
        sb.append(getBTab("tbody"));
        for (List<String> row : this.tbody) {
            sb.append(getBTab("row"));
            for (String cell : row) {
                sb.append(getBTab("c")).append(getCData(cell)).append(getETab("c"));
            }
            sb.append(getETab("row"));
        }
        sb.append(getETab("tbody"));
        sb.append(getETab("table"));
        
        return sb.toString();
    }
    
    /**
     * 转换为清晰字符串（无CDATA）
     */
    public String toClearString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getBTab("table tablename=\"" + this.tablename + "\" type=\"" + this.type + "\" "));
        
        sb.append(getBTab("thead"));
        for (String str : this.thead) {
            sb.append(getBTab("col")).append(str).append(getETab("col"));
        }
        sb.append(getETab("thead"));
        
        sb.append(getBTab("tbody"));
        for (List<String> row : this.tbody) {
            sb.append(getBTab("row"));
            for (String cell : row) {
                sb.append(getBTab("c")).append(cell).append(getETab("c"));
            }
            sb.append(getETab("row"));
        }
        sb.append(getETab("tbody"));
        sb.append(getETab("table"));
        
        return sb.toString();
    }
    
    /**
     * 将表格转化成Map对象数组
     */
    public List<Map<String, Object>> parseToObjList() {
        List<Map<String, Object>> resultList = new ArrayList<>();
        Map<Integer, String> indexMap = new HashMap<>();
        
        if (thead != null && !thead.isEmpty()) {
            for (int i = 0; i < thead.size(); i++) {
                indexMap.put(i, thead.get(i));
            }
        }
        
        if (tbody != null && !tbody.isEmpty()) {
            for (List<String> dList : tbody) {
                if (dList.size() == thead.size()) {
                    Map<String, Object> bMap = new HashMap<>();
                    for (Integer i : indexMap.keySet()) {
                        bMap.put(indexMap.get(i), dList.get(i));
                    }
                    resultList.add(bMap);
                }
            }
        }
        
        return resultList;
    }
    
    private String getBTab(String tabname) {
        return "<" + tabname + ">";
    }
    
    private String getETab(String tabname) {
        return "</" + tabname + ">";
    }
    
    private static String getCData(String v) {
        return "<![CDATA[" + v + "]]>";
    }
    
    // Getters and Setters
    public List<String> getThead() {
        return thead;
    }
    
    public void setThead(List<String> thead) {
        this.thead = thead;
    }
    
    public List<List<String>> getTbody() {
        return tbody;
    }
    
    public void setTbody(List<List<String>> tbody) {
        this.tbody = tbody;
    }
    
    public String getTablename() {
        return tablename;
    }
    
    public void setTablename(String tablename) {
        this.tablename = tablename;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
}