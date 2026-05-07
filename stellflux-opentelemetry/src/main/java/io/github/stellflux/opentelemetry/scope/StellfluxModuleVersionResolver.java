package io.github.stellflux.opentelemetry.scope;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Stellflux 模块版本解析器。 */
public final class StellfluxModuleVersionResolver {

    private static final String GROUP_ID = "io.github.stellhub";

    private static final String UNKNOWN_VERSION = "unknown";

    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

    private StellfluxModuleVersionResolver() {}

    /**
     * 解析模块版本号。
     *
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @return 模块版本号
     */
    public static String resolve(String artifactId, Class<?> anchorClass) {
        String cacheKey = artifactId + "@" + anchorClass.getName();
        return CACHE.computeIfAbsent(cacheKey, ignored -> doResolve(artifactId, anchorClass));
    }

    private static String doResolve(String artifactId, Class<?> anchorClass) {
        String version = resolveFromPomProperties(artifactId, anchorClass);
        if (hasText(version)) {
            return version;
        }
        version = resolveFromImplementationVersion(anchorClass);
        if (hasText(version)) {
            return version;
        }
        version = resolveFromSourcePom(anchorClass);
        if (hasText(version)) {
            return version;
        }
        return UNKNOWN_VERSION;
    }

    private static String resolveFromPomProperties(String artifactId, Class<?> anchorClass) {
        String resourcePath = "META-INF/maven/" + GROUP_ID + "/" + artifactId + "/pom.properties";
        ClassLoader classLoader = anchorClass.getClassLoader();
        try (InputStream inputStream =
                classLoader != null
                        ? classLoader.getResourceAsStream(resourcePath)
                        : ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return normalize(properties.getProperty("version"));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String resolveFromImplementationVersion(Class<?> anchorClass) {
        Package currentPackage = anchorClass.getPackage();
        if (currentPackage == null) {
            return null;
        }
        return normalize(currentPackage.getImplementationVersion());
    }

    private static String resolveFromSourcePom(Class<?> anchorClass) {
        Path pomPath = findPomPath(anchorClass);
        if (pomPath == null) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element project =
                    factory.newDocumentBuilder().parse(Files.newInputStream(pomPath)).getDocumentElement();
            String projectVersion = directChildText(project, "version");
            if (hasText(projectVersion)) {
                return normalize(projectVersion);
            }
            Element parent = directChild(project, "parent");
            if (parent == null) {
                return null;
            }
            return normalize(directChildText(parent, "version"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path findPomPath(Class<?> anchorClass) {
        try {
            URL location = anchorClass.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            Path current = Paths.get(location.toURI());
            if (!Files.isDirectory(current)) {
                current = current.getParent();
            }
            for (int i = 0; i < 6 && current != null; i++, current = current.getParent()) {
                Path pomPath = current.resolve("pom.xml");
                if (Files.isRegularFile(pomPath)) {
                    return pomPath;
                }
            }
            return null;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static Element directChild(Element parent, String name) {
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String directChildText(Element parent, String name) {
        Element child = directChild(parent, name);
        if (child == null) {
            return null;
        }
        return child.getTextContent();
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
