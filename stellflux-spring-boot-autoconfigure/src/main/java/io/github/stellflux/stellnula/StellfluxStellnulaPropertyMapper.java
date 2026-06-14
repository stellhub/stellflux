package io.github.stellflux.stellnula;

import io.github.stellnula.config.StellnulaConfigEntry;
import io.github.stellnula.config.StellnulaSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Stellnula 配置项到 Spring PropertySource 属性的映射器。 */
final class StellfluxStellnulaPropertyMapper {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaPropertyMapper.class.getName());

    private StellfluxStellnulaPropertyMapper() {}

    /**
     * 将 Stellnula 快照转换为 Spring 可解析的扁平属性。
     *
     * @param snapshot Stellnula 配置快照
     * @return Spring 扁平配置属性
     */
    static Map<String, String> toProperties(StellnulaSnapshot snapshot) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (snapshot == null) {
            return properties;
        }
        for (StellnulaConfigEntry entry : snapshot.entries()) {
            if (entry.deleted()) {
                continue;
            }
            properties.putAll(toProperties(entry));
        }
        return properties;
    }

    private static Map<String, String> toProperties(StellnulaConfigEntry entry) {
        if (isYaml(entry)) {
            return yamlToProperties(entry);
        }
        if (isProperties(entry)) {
            return propertiesToProperties(entry);
        }
        if (isToml(entry)) {
            return tomlToProperties(entry);
        }
        if (isXml(entry)) {
            return xmlToProperties(entry);
        }
        return Map.of(entry.configKey(), entry.configValue());
    }

    private static Map<String, String> yamlToProperties(StellnulaConfigEntry entry) {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(
                new ByteArrayResource(
                        entry.configValue().getBytes(StandardCharsets.UTF_8), entry.configKey()));
        Properties loaded = factoryBean.getObject();
        return loaded == null ? Map.of() : toStringMap(loaded);
    }

    private static Map<String, String> propertiesToProperties(StellnulaConfigEntry entry) {
        Properties loaded = new Properties();
        try {
            loaded.load(new StringReader(entry.configValue()));
            return toStringMap(loaded);
        } catch (IOException ex) {
            LOGGER.log(
                    Level.WARNING,
                    ex,
                    () -> "Failed to parse Stellnula properties config: " + entry.configKey());
            return Map.of(entry.configKey(), entry.configValue());
        }
    }

    private static Map<String, String> tomlToProperties(StellnulaConfigEntry entry) {
        try {
            Map<String, String> result = new LinkedHashMap<>();
            String section = "";
            for (String rawLine : entry.configValue().split("\\R")) {
                String line = stripTomlComment(rawLine).trim();
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = normalizeTomlSection(line);
                    continue;
                }
                int separatorIndex = indexOfTomlSeparator(line);
                if (separatorIndex < 0) {
                    continue;
                }
                String key = normalizeTomlKey(line.substring(0, separatorIndex));
                String value = normalizeTomlValue(line.substring(separatorIndex + 1).trim());
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                result.put(joinPropertyName(section, key), value);
            }
            return result;
        } catch (RuntimeException ex) {
            LOGGER.log(
                    Level.WARNING,
                    ex,
                    () -> "Failed to parse Stellnula TOML config: " + entry.configKey());
            return Map.of(entry.configKey(), entry.configValue());
        }
    }

    private static Map<String, String> xmlToProperties(StellnulaConfigEntry entry) {
        byte[] content = entry.configValue().getBytes(StandardCharsets.UTF_8);
        Properties loaded = new Properties();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            loaded.loadFromXML(inputStream);
            return toStringMap(loaded);
        } catch (IOException ignored) {
            // Continue with generic XML flattening below.
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setXmlFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setXmlFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setXmlFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(inputStream);
            Map<String, String> result = new LinkedHashMap<>();
            flattenXmlElement(document.getDocumentElement(), "", result);
            return result;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            LOGGER.log(
                    Level.WARNING,
                    ex,
                    () -> "Failed to parse Stellnula XML config: " + entry.configKey());
            return Map.of(entry.configKey(), entry.configValue());
        }
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        properties.forEach((key, val) -> result.put(String.valueOf(key), String.valueOf(val)));
        return result;
    }

    private static void flattenXmlElement(
            Element element, String parentPath, Map<String, String> result) {
        String path = joinPropertyName(parentPath, element.getTagName());
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            result.put(joinPropertyName(path, attribute.getNodeName()), attribute.getNodeValue());
        }

        NodeList childNodes = element.getChildNodes();
        Map<String, Integer> childCounts = countChildElements(childNodes);
        Map<String, Integer> childIndexes = new LinkedHashMap<>();
        boolean hasElementChild = false;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element childElement) {
                hasElementChild = true;
                String childName = childElement.getTagName();
                int index = childIndexes.merge(childName, 1, Integer::sum) - 1;
                String childPath = path;
                if (childCounts.getOrDefault(childName, 0) > 1) {
                    childPath = path + "." + childName + "[" + index + "]";
                    flattenXmlElementChildren(childElement, childPath, result);
                } else {
                    flattenXmlElement(childElement, path, result);
                }
            } else if (childNode.getNodeType() == Node.TEXT_NODE
                    || childNode.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(childNode.getTextContent());
            }
        }

        if (!hasElementChild) {
            String value = text.toString().trim();
            if (StringUtils.hasText(value)) {
                result.put(path, value);
            }
        }
    }

    private static void flattenXmlElementChildren(
            Element element, String path, Map<String, String> result) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            result.put(joinPropertyName(path, attribute.getNodeName()), attribute.getNodeValue());
        }

        NodeList childNodes = element.getChildNodes();
        Map<String, Integer> childCounts = countChildElements(childNodes);
        Map<String, Integer> childIndexes = new LinkedHashMap<>();
        boolean hasElementChild = false;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element childElement) {
                hasElementChild = true;
                String childName = childElement.getTagName();
                int index = childIndexes.merge(childName, 1, Integer::sum) - 1;
                if (childCounts.getOrDefault(childName, 0) > 1) {
                    flattenXmlElementChildren(
                            childElement, path + "." + childName + "[" + index + "]", result);
                } else {
                    flattenXmlElement(childElement, path, result);
                }
            } else if (childNode.getNodeType() == Node.TEXT_NODE
                    || childNode.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(childNode.getTextContent());
            }
        }

        if (!hasElementChild) {
            String value = text.toString().trim();
            if (StringUtils.hasText(value)) {
                result.put(path, value);
            }
        }
    }

    private static Map<String, Integer> countChildElements(NodeList childNodes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element childElement) {
                counts.merge(childElement.getTagName(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static void setXmlFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // Some XML parsers do not support every hardening flag.
        }
    }

    private static String stripTomlComment(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inDoubleQuote) {
                escaped = true;
                continue;
            }
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (current == '#' && !inSingleQuote && !inDoubleQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String normalizeTomlSection(String line) {
        String section = line;
        while (section.startsWith("[")) {
            section = section.substring(1);
        }
        while (section.endsWith("]")) {
            section = section.substring(0, section.length() - 1);
        }
        return normalizeTomlKey(section);
    }

    private static int indexOfTomlSeparator(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;
        int nestedDepth = 0;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inDoubleQuote) {
                escaped = true;
                continue;
            }
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (current == '[' || current == '{') {
                    nestedDepth++;
                } else if (current == ']' || current == '}') {
                    nestedDepth--;
                } else if (current == '=' && nestedDepth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String normalizeTomlKey(String key) {
        StringBuilder result = new StringBuilder();
        StringBuilder segment = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;
        for (int i = 0; i < key.length(); i++) {
            char current = key.charAt(i);
            if (escaped) {
                segment.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\' && inDoubleQuote) {
                escaped = true;
                continue;
            }
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (current == '.' && !inSingleQuote && !inDoubleQuote) {
                appendTomlKeySegment(result, segment);
                continue;
            }
            segment.append(current);
        }
        appendTomlKeySegment(result, segment);
        return result.toString();
    }

    private static void appendTomlKeySegment(StringBuilder result, StringBuilder segment) {
        String normalized = segment.toString().trim();
        segment.setLength(0);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (!result.isEmpty()) {
            result.append('.');
        }
        result.append(normalized);
    }

    private static String normalizeTomlValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (isQuotedTomlString(trimmed)) {
            return unquoteTomlString(trimmed);
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return normalizeTomlArray(trimmed);
        }
        return trimmed;
    }

    private static boolean isQuotedTomlString(String value) {
        return value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")));
    }

    private static String unquoteTomlString(String value) {
        String unquoted = value.substring(1, value.length() - 1);
        if (value.startsWith("'")) {
            return unquoted;
        }
        return unquoted
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static String normalizeTomlArray(String value) {
        String arrayContent = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder();
        StringBuilder item = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;
        int nestedDepth = 0;
        for (int i = 0; i < arrayContent.length(); i++) {
            char current = arrayContent.charAt(i);
            if (escaped) {
                item.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\' && inDoubleQuote) {
                escaped = true;
                item.append(current);
                continue;
            }
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                item.append(current);
                continue;
            }
            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                item.append(current);
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (current == '[' || current == '{') {
                    nestedDepth++;
                } else if (current == ']' || current == '}') {
                    nestedDepth--;
                } else if (current == ',' && nestedDepth == 0) {
                    appendTomlArrayItem(result, item);
                    continue;
                }
            }
            item.append(current);
        }
        appendTomlArrayItem(result, item);
        return result.toString();
    }

    private static void appendTomlArrayItem(StringBuilder result, StringBuilder item) {
        String normalized = normalizeTomlValue(item.toString());
        item.setLength(0);
        if (!result.isEmpty()) {
            result.append(',');
        }
        result.append(normalized);
    }

    private static String joinPropertyName(String parent, String child) {
        if (!StringUtils.hasText(parent)) {
            return child;
        }
        if (!StringUtils.hasText(child)) {
            return parent;
        }
        return parent + "." + child;
    }

    private static boolean isYaml(StellnulaConfigEntry entry) {
        return hasContentType(entry, "YAML")
                || hasExtension(entry.configKey(), ".yaml")
                || hasExtension(entry.configKey(), ".yml");
    }

    private static boolean isProperties(StellnulaConfigEntry entry) {
        return hasContentType(entry, "PROPERTIES") || hasExtension(entry.configKey(), ".properties");
    }

    private static boolean isToml(StellnulaConfigEntry entry) {
        return hasContentType(entry, "TOML") || hasExtension(entry.configKey(), ".toml");
    }

    private static boolean isXml(StellnulaConfigEntry entry) {
        return hasContentType(entry, "XML") || hasExtension(entry.configKey(), ".xml");
    }

    private static boolean hasContentType(StellnulaConfigEntry entry, String token) {
        return StringUtils.hasText(entry.contentType())
                && entry.contentType().toUpperCase().contains(token);
    }

    private static boolean hasExtension(String configKey, String extension) {
        return StringUtils.hasText(configKey) && configKey.toLowerCase().endsWith(extension);
    }
}
