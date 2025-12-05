package other.xsy.jstforaspvportal.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rkhd.platform.sdk.exception.ApiEntityServiceException;
import com.rkhd.platform.sdk.log.Logger;
import com.rkhd.platform.sdk.log.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * @Description: 用于解析 XML 并创建等效 JSON 的实用程序类
 * @Author: 武于伦
 * @email: 2717718875@qq.com
 * @Date: 2025/8/22
 */
public final class XMLParser {

    //    private static final Logger log = LoggerFactory.getLogger(XMLParser.class);
    private static final Logger log = LoggerFactory.getLogger();
    // To find the root element so that we can enclose it in the curly braces
    private static String rootElementName;

    private XMLParser() {}

    /**
     * Parse the XML content into JSON string.
     * @param xml XML string
     * @return JSON string
     */
    public static String xmlToJson(String xml) {
        try {
            rootElementName = null; // reset per invocation
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new java.io.ByteArrayInputStream(stripCDATA(xml).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            String jsonContent = parse(root, false);
            return jsonContent;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XML to JSON: " + e.getMessage(), e);
        }
    }

    public static String stripCDATA(String str) {
        Pattern p = Pattern.compile("<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL);
        Matcher m = p.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String content = escapeXml(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(content));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String escapeXml(String input) {
        if (input == null) return null;
        String s = input;
        s = s.replace("&", "&amp;");
        s = s.replace("<", "&lt;");
        s = s.replace(">", "&gt;");
        s = s.replace("\"", "&quot;");
        s = s.replace("'", "&apos;");
        return s;
    }

    /**
     * Recursive method to process each XML node and construct JSON string.
     * Mirrors the Apex implementation details, including special handling for arrays and empty values.
     */
    public static String parse(Node node, boolean isChild) {
        String json = "";
        boolean isArray = false;

        if (rootElementName == null) {
            rootElementName = node.getNodeName();
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Map<String, List<String>> mapChildrenJSON = new HashMap<String, List<String>>();
            List<String> lstJSONForChildren = new ArrayList<String>();

            // Process children
            List<Node> childElements = getChildElements(node);
            for (Node child : childElements) {
                String tmp = parse(child, true);

                if (tmp != null && !tmp.isEmpty()) {
                    mapChildrenJSON.computeIfAbsent(child.getNodeName(), k -> new ArrayList<String>()).add(tmp);
                } else { // keep structure even for empty values in specific parents
                    Node parent = child.getParentNode();
                    String parentName = parent != null ? parent.getNodeName() : "";
                    if ("row".equals(parentName) || "fields".equals(parentName)) {
                        mapChildrenJSON.computeIfAbsent(child.getNodeName(), k -> new ArrayList<String>()).add("\"\"");
                    }
                }
            }

            // Structure JSON based on repetition
            for (Map.Entry<String, List<String>> entry : mapChildrenJSON.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();

                if (values.size() > 1 ||
                        "tables".equals(node.getNodeName()) ||
                        "thead".equals(node.getNodeName()) ||
                        "tbody".equals(node.getNodeName())) {
                    if (isChild) {
                        lstJSONForChildren.add('[' + String.join(", ", values) + ']');
                    } else {
                        lstJSONForChildren.add('"' + key + '"' + ": [" + String.join(", ", values) + "]");
                    }
                    isArray = true;
                } else {
                    lstJSONForChildren.add('"' + key + '"' + ": " + values.get(0));
                }
            }

            // Add attributes
            List<String> lstAttributes = new ArrayList<String>(lstJSONForChildren);
            NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    Node a = attrs.item(i);
                    String key = a.getNodeName();
                    String value = a.getNodeValue() != null ? a.getNodeValue() : "";
                    lstAttributes.add('"' + key + '"' + ": " + '"' + value + '"');
                }
            }

            // Text content: 修改为与Apex版本一致的逻辑
            String textContent = getDirectTextContent(node);
            if (textContent != null && textContent.trim().length() > 0) {
                textContent = textContent.replace("\"", "\\\"");
                lstAttributes.add("\"ele_text\": \"" + textContent + "\"");
            }

            if (!isChild) {
                if (!isArray) {
                    json = '"' + node.getNodeName() + '"' + ": {" + String.join(", ", lstAttributes) + '}';
                } else {
                    json = " {" + String.join(", ", lstAttributes) + "}";
                }
            } else {
                if (lstAttributes.size() == 1 && textContent != null && textContent.trim().length() > 0) {
                    json = '"' + textContent + '"';
                } else {
                    if (!isArray) {
                        if (!lstAttributes.isEmpty()) {
                            json = '{' + String.join(", ", lstAttributes) + '}';
                        }
                    } else {
                        json = String.join(", ", lstAttributes);
                    }
                }
            }
        }

        if (rootElementName.equals(node.getNodeName())) {
            if (!isArray) {
                json = '{' + json + '}';
            } else {
                json = '"' + node.getNodeName() + '"' + " : " + json;
                json = '{' + json + '}';
            }
        }

        System.out.println("*** " + node.getNodeName() + ": " + json);

        return json;
    }

    private static List<Node> getChildElements(Node node) {
        List<Node> list = new ArrayList<Node>();
        NodeList children = node.getChildNodes();
        if (children == null) return list;
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                list.add(c);
            }
        }
        return list;
    }

    /**
     * 获取节点的直接文本内容，不包括子节点的文本
     * 模拟Apex的getText()方法行为
     */
    private static String getDirectTextContent(Node node) {
        StringBuilder textContent = new StringBuilder();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = child.getNodeValue();
                if (text != null) {
                    textContent.append(text);
                }
            }
        }

        return textContent.toString().trim();
    }
    /**
     * 处理XML字符串的HTML转义和反转义
     * 对应Apex代码中的escapeHtml4、unescapeHtml4等操作
     * 使用纯Java实现，不依赖外部库
     * @param xmlStr 原始XML字符串
     * @return 处理后的XML字符串
     */
    public static String processXmlString(String xmlStr) {
        if (xmlStr == null || xmlStr.isEmpty()) {
            return xmlStr;
        }

        // 对应 xmlStr=xmlStr.escapeHtml4();
        xmlStr = escapeHtml4(xmlStr);
        System.out.println("*** xmlStr after escapeHtml4: " + xmlStr);

        // 对应 xmlStr=xmlStr.replaceAll('\\\\&quot;','"');
        xmlStr = xmlStr.replaceAll("\\\\&quot;", "\"");

        // 对应 xmlStr = xmlStr.replaceAll('\\\\', '\\\\\\\\');
        xmlStr = xmlStr.replaceAll("\\\\", "\\\\\\\\");

        // 对应 xmlStr=xmlStr.unescapeHtml4();
        xmlStr = unescapeHtml4(xmlStr);

        return xmlStr;
    }

    /**
     * HTML4转义方法（纯Java实现）
     * @param input 待转义的字符串
     * @return 转义后的字符串
     */
    private static String escapeHtml4(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '&':
                    escaped.append("&amp;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }

    /**
     * HTML4反转义方法（纯Java实现）
     * @param input 待反转义的字符串
     * @return 反转义后的字符串
     */
    private static String unescapeHtml4(String input) {
        if (input == null) {
            return null;
        }

        String result = input;
        result = result.replace("&lt;", "<");
        result = result.replace("&gt;", ">");
        result = result.replace("&amp;", "&");
        result = result.replace("&quot;", "\"");
        result = result.replace("&#39;", "'");
        result = result.replace("&apos;", "'");

        return result;
    }
    /**
     * 清理XML字符串，移除XML文档后的额外内容
     * @param xmlStr 原始XML字符串
     * @return 清理后的XML字符串
     */
    public static String cleanXmlString(String xmlStr) {
        if (xmlStr == null || xmlStr.isEmpty()) {
            return xmlStr;
        }

        // 找到最后一个根元素的结束标签
        int lastRootTagEnd = findLastRootElementEnd(xmlStr);
        if (lastRootTagEnd > 0 && lastRootTagEnd < xmlStr.length()) {
            // 截取到根元素结束位置
            xmlStr = xmlStr.substring(0, lastRootTagEnd);
        }

        return xmlStr;
    }

    /**
     * 查找最后一个根元素的结束位置
     * @param xmlStr XML字符串
     * @return 结束位置
     */
    private static int findLastRootElementEnd(String xmlStr) {
        // 查找所有可能的根元素结束标签
        String[] possibleRootTags = {"</response>", "</request>", "</root>", "</xml>"};

        int maxIndex = -1;
        for (String tag : possibleRootTags) {
            int index = xmlStr.lastIndexOf(tag);
            if (index > maxIndex) {
                maxIndex = index + tag.length();
            }
        }

        return maxIndex;
    }

    /**
     * 将解析后的JSON转换为期望的结构化格式
     * @param jsonStr 原始JSON字符串
     * @return 转换后的JSON对象
     */
    public static JSONObject transformToExpectedFormat(String jsonStr) {
        try {
            JSONObject originalJson = JSON.parseObject(jsonStr);
            JSONObject request = originalJson.getJSONObject("request");

            // 创建结果对象
            JSONObject result = new JSONObject();

            // 处理fields部分 - 提取bidserno等字段
            if (request.containsKey("fields")) {
                JSONObject fields = request.getJSONObject("fields");
                for (String key : fields.keySet()) {
                    result.put(key, fields.get(key));
                }
            }

            // 处理tables部分 - 转换表格数据为结构化对象数组
            if (request.containsKey("tables") && request.get("tables") instanceof JSONArray) {
                JSONArray tables = request.getJSONArray("tables");

                for (int i = 0; i < tables.size(); i++) {
                    JSONObject table = tables.getJSONObject(i);
                    String tableName = table.getString("tablename");

                    if (table.containsKey("thead") && table.containsKey("tbody")) {
                        JSONArray thead = table.getJSONArray("thead");
                        JSONArray tbody = table.getJSONArray("tbody");

                        // 创建结构化的表格数据
                        JSONArray structuredData = new JSONArray();

                        for (int rowIndex = 0; rowIndex < tbody.size(); rowIndex++) {
                            JSONArray row = tbody.getJSONArray(rowIndex);
                            JSONObject rowObject = new JSONObject();

                            // 将列头与行数据关联
                            for (int colIndex = 0; colIndex < thead.size() && colIndex < row.size(); colIndex++) {
                                String columnName = thead.getString(colIndex);
                                String cellValue = row.getString(colIndex);
                                rowObject.put(columnName, cellValue);
                            }

                            structuredData.add(rowObject);
                        }

                        result.put(tableName, structuredData);
                    }
                }
            }

            return result;

        } catch (Exception e) {
            log.error("转换JSON格式时发生错误: " + e.getMessage(), e);
            throw new RuntimeException("转换JSON格式失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将tables数据合并到fields中，用于inbound接口处理
     * @param requestJson 包含fields和tables的request JSON对象
     * @return 合并后的fields JSON对象
     */
    public static JSONObject mergeTablesToFields(JSONObject requestJson) {
        try {
            JSONObject result = new JSONObject();

            // 首先复制所有fields数据
            if (requestJson.containsKey("fields")) {
                JSONObject fields = requestJson.getJSONObject("fields");
                for (String key : fields.keySet()) {
                    result.put(key, fields.get(key));
                }
            }
            // head 数据也传入
            if (requestJson.containsKey("head")){
                JSONObject head = requestJson.getJSONObject("head");
                result.put("head", head);
            }
            // 然后处理tables数据，将表格数据合并到fields中
            if (requestJson.containsKey("tables") && requestJson.get("tables") instanceof JSONArray) {
                JSONArray tables = requestJson.getJSONArray("tables");

                for (int i = 0; i < tables.size(); i++) {
                    JSONObject table = tables.getJSONObject(i);
                    String tableName = table.getString("tablename");

                    if (table.containsKey("thead") && table.containsKey("tbody")) {
                        JSONArray thead = table.getJSONArray("thead");
                        JSONArray tbody = table.getJSONArray("tbody");

                        // 构建结构化的表格数据（无论行数多少都生成）
                        JSONArray structuredData = new JSONArray();
                        for (int rowIndex = 0; rowIndex < tbody.size(); rowIndex++) {
                            JSONArray row = tbody.getJSONArray(rowIndex);
                            JSONObject rowObject = new JSONObject();

                            // 将列头与行数据关联
                            for (int colIndex = 0; colIndex < thead.size() && colIndex < row.size(); colIndex++) {
                                String columnName = thead.getString(colIndex);
                                String cellValue = row.getString(colIndex);
                                rowObject.put(columnName, cellValue);
                            }

                            structuredData.add(rowObject);
                        }

                        // 仅放入以表名为键的数组结构（如 VisitorInfo: [{...}]），不再平铺到fields
                        result.put(tableName, structuredData);
                    }
                }
            }

            return result;

        } catch (Exception e) {
            log.error("合并tables到fields时发生错误: " + e.getMessage(), e);
            throw new RuntimeException("合并tables到fields失败: " + e.getMessage(), e);
        }
    }

    /**
     * 递归预处理：将字符串中“类似JSON”的内容还原为真正的JSONObject/JSONArray
     * 用于在合并完成后、映射前做一次清洗，例如把 "[ {\"a\":1} ]" 变成 JSONArray
     */
    public static Object preprocessPotentialJson(Object data) {
        if (data == null) return null;

        if (data instanceof JSONObject) {
            JSONObject obj = (JSONObject) data;
            for (String key : new ArrayList<>(obj.keySet())) {
                Object v = obj.get(key);
                Object nv = preprocessPotentialJson(v);
                if (nv != v) {
                    obj.put(key, nv);
                }
            }
            // 特殊处理 jsondata：如果其内容是JSONObject或仅包含一个JSONObject的JSONArray，则将其平铺到当前对象并移除jsondata键
            if (obj.containsKey("jsondata")) {
                Object jv = obj.get("jsondata");
                try {
                    if (jv instanceof JSONObject) {
                        JSONObject jo = (JSONObject) jv;
                        for (String k : new ArrayList<>(jo.keySet())) {
                            obj.put(k, jo.get(k));
                        }
                        obj.remove("jsondata");
                    } else if (jv instanceof JSONArray) {
                        JSONArray ja = (JSONArray) jv;
                        if (ja.size() == 1 && ja.get(0) instanceof JSONObject) {
                            JSONObject jo = ja.getJSONObject(0);
                            for (String k : new ArrayList<>(jo.keySet())) {
                                obj.put(k, jo.get(k));
                            }
                            obj.remove("jsondata");
                        }
                    } else if (jv instanceof String) {
                        // 处理jsondata为字符串的情况，需要先解析
                        String jsondataStr = (String) jv;
                        if (looksLikeJson(jsondataStr)) {
                            String unescaped = unescapeForJson(jsondataStr);
                            try {
                                // 尝试解析为JSONArray
                                JSONArray ja = JSONArray.parseArray(unescaped);
                                if (ja.size() == 1 && ja.get(0) instanceof JSONObject) {
                                    JSONObject jo = ja.getJSONObject(0);
                                    for (String k : new ArrayList<>(jo.keySet())) {
                                        obj.put(k, jo.get(k));
                                    }
                                    obj.remove("jsondata");
                                }
                            } catch (Exception e) {
                                // 尝试解析为JSONObject
                                try {
                                    JSONObject jo = JSONObject.parseObject(unescaped);
                                    for (String k : new ArrayList<>(jo.keySet())) {
                                        obj.put(k, jo.get(k));
                                    }
                                    obj.remove("jsondata");
                                } catch (Exception e2) {
                                    log.error("jsondata字段JSON字符串去转义失败");
                                    log.error("原始jsondata字符串: " + jsondataStr);
                                    log.error("去转义后字符串: " + unescaped);
                                    log.error("JSONArray解析错误: " + e.getMessage());
                                    log.error("JSONObject解析错误: " + e2.getMessage());
                                    throw new RuntimeException("jsondata字段JSON字符串去转义失败: " + e2.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("处理jsondata字段时发生错误: " + e.getMessage(), e);
                    throw new RuntimeException("处理jsondata字段失败: " + e.getMessage(), e);
                }
            }
            return obj;
        }

        if (data instanceof JSONArray) {
            JSONArray arr = (JSONArray) data;
            for (int i = 0; i < arr.size(); i++) {
                Object v = arr.get(i);
                Object nv = preprocessPotentialJson(v);
                if (nv != v) {
                    arr.set(i, nv);
                }
            }
            return arr;
        }

        if (data instanceof String) {
            String s = ((String) data).trim();
            if (looksLikeJson(s)) {
                String unescaped = unescapeForJson(s);
                // 优先尝试数组
                try {
                    JSONArray ja = JSONArray.parseArray(unescaped);
                    return preprocessPotentialJson(ja);
                } catch (Exception e) {
                    log.warn("JSON数组解析失败，尝试解析为对象: " + e.getMessage());
                }
                // 再尝试对象
                try {
                    JSONObject jo = JSONObject.parseObject(unescaped);
                    return preprocessPotentialJson(jo);
                } catch (Exception e) {
                    log.error("JSON解析失败，原始字符串: " + s);
                    log.error("去转义后字符串: " + unescaped);
                    log.error("解析错误: " + e.getMessage());
                    // 如果解析失败，返回原始字符串而不是抛出异常
                    return s;
                }
            }
            return data;
        }

        return data;
    }

    private static boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        if ((t.startsWith("[") && t.endsWith("]")) || (t.startsWith("{") && t.endsWith("}"))) {
            return true;
        }
        // 含有大量转义符也尝试
        return t.contains("\\\\{") || t.contains("\\\\[") || t.contains("\\\\\"");
    }

    private static String unescapeForJson(String s) {
        if (s == null) return null;
        String r = s;
        
        // 1. 预处理：保护文本中的引号和特殊字符
        r = protectTextContent(r);
        
        // 2. 修复空字符串和引号问题
        r = r.replaceAll("\"([^\"]+)\"\\s*:", "\"$1\":");  // 确保键名格式正确
        r = r.replaceAll(":\\s*,", ":null,");              // 将空值替换为null
        r = r.replaceAll(":\\s*\"\"\\s*,", ":null,");     // 将空字符串替换为null
        r = r.replaceAll(":\\s*\"\"\\s*}", ":null}");     // 处理对象末尾的空字符串

        // 3. 处理特殊的转义序列
        r = r.replace("\\n", "\\\\n");                     // 统一换行符格式
        r = r.replace("\\r", "\\\\r");                     // 统一回车符格式
        r = r.replace("\\t", "\\\\t");                     // 统一制表符格式
        r = r.replace("\\/", "/");                         // 处理斜杠

        // 4. 修复数值字段（但保留带前导零的数字字符串）
        // 不移除带前导零的数字字符串的引号，避免前导零丢失
        r = r.replaceAll("\"([1-9][0-9]*\\.?[0-9]*)\"(?=,|\\})", "$1"); // 只移除不带前导零的数值周围的引号

        // 5. 最后的清理
        r = r.replaceAll("\\s+", " ");                     // 压缩空白字符
        r = r.replaceAll("(?<!\\\\)\"\"", "\"");          // 处理未转义的连续引号
        
        // 6. 恢复被保护的文本内容
        r = restoreTextContent(r);
        
        return r;
    }
    
    /**
     * 保护文本内容中的特殊字符
     */
    private static String protectTextContent(String input) {
        if (input == null) return null;
        
        StringBuilder result = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        char[] chars = input.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            
            if (escaped) {
                if (c == '\"') {
                    result.append("\\\"");
                } else if (c == '\\') {
                    result.append("\\\\");
                } else {
                    result.append('\\').append(c);
                }
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '\"') {
                if (!inQuotes) {
                    // 开始引号
                    inQuotes = true;
                    result.append(c);
                } else {
                    // 检查是否是字段结束
                    if (i + 1 < chars.length && (chars[i + 1] == ',' || chars[i + 1] == '}' || chars[i + 1] == ':')) {
                        inQuotes = false;
                        result.append(c);
                    } else {
                        // 文本内容中的引号
                        result.append("\\\"");
                    }
                }
                continue;
            }
            
            if (inQuotes) {
                // 在引号内的内容需要处理特殊字符
                switch (c) {
                    case '\n': result.append("\\n"); break;
                    case '\r': result.append("\\r"); break;
                    case '\t': result.append("\\t"); break;
                    case '/': result.append("\\/"); break;
                    default: result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 恢复被保护的文本内容
     */
    private static String restoreTextContent(String input) {
        if (input == null) return null;
        
        // 处理可能的特殊字符
        String result = input;
        
        // 确保JSON字符串中的转义字符格式正确
        result = result.replace("\\\\\"", "\\\"");  // 修复重复转义的引号
        result = result.replace("\\\\n", "\\n");    // 修复重复转义的换行符
        result = result.replace("\\\\r", "\\r");    // 修复重复转义的回车符
        result = result.replace("\\\\t", "\\t");    // 修复重复转义的制表符
        
        return result;
    }

    /**
     * 处理已经转换为JSON的数据，专门用于处理类似你提供的JSON格式
     * @param jsonData 已经转换好的JSON字符串
     * @return 处理后的JSON对象，包含合并的fields和tables数据
     */
    public static JSONObject processPreConvertedJson(String jsonData) {
        try {
            JSONObject originalJson = JSON.parseObject(jsonData);
            JSONObject request = originalJson.getJSONObject("request");

            if (request == null) {
                throw new RuntimeException("JSON数据中缺少request对象");
            }

            // 创建结果对象
            JSONObject result = new JSONObject();

            // 处理head数据
            if (request.containsKey("head")) {
                JSONObject head = request.getJSONObject("head");
                result.put("head", head);
            }

            // 处理fields数据
            if (request.containsKey("fields")) {
                JSONObject fields = request.getJSONObject("fields");
                for (String key : fields.keySet()) {
                    result.put(key, fields.get(key));
                }
            }

            // 处理tables数据 - 兼容不同形态（对象/数组/字符串/空）
            if (request.containsKey("tables")) {
                Object tablesAny = request.get("tables");
                // 将可能的字符串尝试解析为JSON
                if (tablesAny instanceof String) {
                    String s = ((String) tablesAny).trim();
                    if (!s.isEmpty() && looksLikeJson(s)) {
                        try {
                            tablesAny = preprocessPotentialJson(s);
                        } catch (Exception e) {
                            log.error("tables字段JSON字符串解析失败: " + e.getMessage(), e);
                            throw new RuntimeException("tables字段JSON字符串解析失败: " + e.getMessage(), e);
                        }
                    } else {
                        tablesAny = null; // 空字符串或非JSON字符串，忽略
                    }
                }

                if (tablesAny instanceof JSONObject) {
                    JSONObject tables = (JSONObject) tablesAny;
                    if (tables.containsKey("table")) {
                        JSONObject table = tables.getJSONObject("table");
                        String tableName = table.getString("tablename");

                        if (table.containsKey("thead") && table.containsKey("tbody")) {
                            Object theadAny = table.get("thead");
                            Object tbodyAny = table.get("tbody");

                            // 跳过空字符串或null
                            boolean theadEmpty = (theadAny == null) || (theadAny instanceof String && ((String) theadAny).trim().isEmpty());
                            boolean tbodyEmpty = (tbodyAny == null) || (tbodyAny instanceof String && ((String) tbodyAny).trim().isEmpty());
                            if (theadEmpty || tbodyEmpty) {
                                // 不处理空表头/空表体
                            } else if (theadAny instanceof JSONObject && tbodyAny instanceof JSONObject) {
                                JSONObject thead = (JSONObject) theadAny;
                                JSONObject tbody = (JSONObject) tbodyAny;
                                if (thead.containsKey("col") && tbody.containsKey("row")) {
                                    JSONArray colHeaders = thead.getJSONArray("col");
                                    JSONArray rows = tbody.getJSONArray("row");
                                    if (colHeaders != null && rows != null) {
                                        JSONArray structuredData = new JSONArray();
                                        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                                            JSONObject row = rows.getJSONObject(rowIndex);
                                            JSONArray cells = row.getJSONArray("c");
                                            if (cells == null) cells = new JSONArray();

                                            JSONObject rowObject = new JSONObject();
                                            // 将列头与行数据关联
                                            for (int colIndex = 0; colIndex < colHeaders.size() && colIndex < cells.size(); colIndex++) {
                                                String columnName = colHeaders.getString(colIndex);
                                                String cellValue = cells.getString(colIndex);
                                                rowObject.put(columnName, cellValue);
                                            }
                                            structuredData.add(rowObject);
                                        }
                                        // 将表格数据添加到结果中
                                        result.put(tableName, structuredData);
                                    }
                                }
                            } else if (theadAny instanceof JSONArray && tbodyAny instanceof JSONArray) {
                                // 兼容 thead 为列名数组，tbody 为行数组
                                JSONArray thead = (JSONArray) theadAny;
                                JSONArray tbody = (JSONArray) tbodyAny;
                                JSONArray structuredData = new JSONArray();
                                for (int rowIndex = 0; rowIndex < tbody.size(); rowIndex++) {
                                    JSONArray row = tbody.getJSONArray(rowIndex);
                                    if (row == null) continue;
                                    JSONObject rowObject = new JSONObject();
                                    for (int colIndex = 0; colIndex < thead.size() && colIndex < row.size(); colIndex++) {
                                        String columnName = thead.getString(colIndex);
                                        String cellValue = row.getString(colIndex);
                                        rowObject.put(columnName, cellValue);
                                    }
                                    structuredData.add(rowObject);
                                }
                                result.put(tableName, structuredData);
                            }
                        }
                    }
                } else if (tablesAny instanceof JSONArray) {
                    // 兼容数组形态：[{tablename, thead:[], tbody:[]}, ...]
                    JSONArray tablesArr = (JSONArray) tablesAny;
                    for (int i = 0; i < tablesArr.size(); i++) {
                        Object ti = tablesArr.get(i);
                        if (!(ti instanceof JSONObject)) continue;
                        JSONObject table = (JSONObject) ti;
                        String tableName = table.getString("tablename");
                        if (table.containsKey("thead") && table.containsKey("tbody")) {
                            JSONArray thead = table.getJSONArray("thead");
                            JSONArray tbody = table.getJSONArray("tbody");

                            JSONArray structuredData = new JSONArray();
                            for (int rowIndex = 0; rowIndex < tbody.size(); rowIndex++) {
                                JSONArray row = tbody.getJSONArray(rowIndex);
                                JSONObject rowObject = new JSONObject();
                                for (int colIndex = 0; colIndex < thead.size() && colIndex < row.size(); colIndex++) {
                                    String columnName = thead.getString(colIndex);
                                    String cellValue = row.getString(colIndex);
                                    rowObject.put(columnName, cellValue);
                                }
                                structuredData.add(rowObject);
                            }
                            result.put(tableName, structuredData);
                        }
                    }
                }
            }

            // 进行递归预处理，处理可能的JSON字符串
            result = (JSONObject) preprocessPotentialJson(result);

            return result;

        } catch (Exception e) {
            log.error("处理预转换JSON数据时发生错误: " + e.getMessage(), e);
            throw new RuntimeException("处理预转换JSON数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 简化的JSON处理方法，专门用于处理你提供的JSON格式
     * @param jsonData 已经转换好的JSON字符串
     * @return 处理后的JSON字符串
     */
    public static String processJsonData(String jsonData) {
        JSONObject processed = processPreConvertedJson(jsonData);
        return processed.toJSONString();
    }


    public static void main(String[] args) throws ApiEntityServiceException {

//        String xml2 = "";
        Scanner scanner = new Scanner(System.in);
        System.out.println("输入xml:");
        String xml2 = scanner.nextLine();
        xml2 = cleanXmlString(xml2);
        xml2.replaceAll("\\\\\\\\r","");
        xml2.replaceAll("\\\\\\\\n","");
        xml2.replaceAll("\\\\\\\\t","");
        xml2.replaceAll("\\\\&quot;","“");
        xml2.replaceAll("\\\\", "\\\\\\\\");

        xml2 = processXmlString(xml2);
        System.out.println(xml2);
        String s1 = XMLParser.xmlToJson(xml2);  // 使用当前类的xmlToJson方法
        System.out.println(s1);
        JSONObject json = JSONObject.parseObject(s1);
        String trancode = json.getJSONObject("request").getJSONObject("head").getString("trancode");

        // 获取request对象，包含fields和tables
        JSONObject request = json.getJSONObject("request");

        // 使用新的方法将tables数据合并到fields中
        JSONObject mergedFields = mergeTablesToFields(request);
        // 在映射前进行递归预处理：把可能是JSON的字符串解析为真正的JSON
        mergedFields = (JSONObject) preprocessPotentialJson(mergedFields);

        System.out.println("合并后的fields数据:");
        System.out.println(mergedFields.toJSONString());

        // 使用合并后的fields进行映射
        JSONObject jsonObject = CommoninterfaceUtil.autoMapping(trancode, "inbound", mergedFields);
        System.out.println("最终映射结果:");
        System.out.println(jsonObject);
//        Map<String, Object> innerMap = jsonObject.getInnerMap();
//        System.out.println(innerMap);
//        DeliverySRM__c deliverySRM__c = JSON.parseObject(jsonObject.getJSONObject("deliverySRM__c").toJSONString(), DeliverySRM__c.class);
//        System.out.println(deliverySRM__c);
    }
}


