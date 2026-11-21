package io.github.rkaushikdev.swagebay.testing;

import io.github.rkaushikdev.swagebay.Swagebay;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** The project's own build file and the files beside it, read the way a release check reads them. */
public final class BuildFile {

    private final Path root;
    private final Element project;

    private BuildFile(Path root, Element project) {
        this.root = root;
        this.project = project;
    }

    /** The build file of the project these classes were compiled from. */
    public static BuildFile ofThisProject() {
        Path root = root();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document =
                    factory.newDocumentBuilder().parse(root.resolve("pom.xml").toFile());
            return new BuildFile(root, document.getDocumentElement());
        } catch (ParserConfigurationException | SAXException | IOException unreadable) {
            throw new IllegalStateException("the build file cannot be read", unreadable);
        }
    }

    /** The project directory: the nearest directory above the compiled classes that holds a build file. */
    public static Path root() {
        try {
            Path at = Path.of(Swagebay.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            while (at != null && !Files.isRegularFile(at.resolve("pom.xml"))) {
                at = at.getParent();
            }
            if (at == null) {
                throw new IllegalStateException("no build file above the compiled classes");
            }
            return at;
        } catch (URISyntaxException unreadable) {
            throw new IllegalStateException("the class location is not a path", unreadable);
        }
    }

    /** A file of the project, by its path from the project directory. */
    public Path file(String relative) {
        return root.resolve(relative);
    }

    /** The text of a top-level element of the project, such as {@code version}. */
    public Optional<String> value(String name) {
        return child(project, name).map(Node::getTextContent).map(String::strip);
    }

    /** A property the build declares. */
    public Optional<String> property(String name) {
        return child(project, "properties")
                .flatMap(properties -> child(properties, name))
                .map(Node::getTextContent);
    }

    /** The version of the parent build, if there is one. */
    public Optional<String> parentVersion() {
        return child(project, "parent")
                .flatMap(parent -> child(parent, "version"))
                .map(Node::getTextContent);
    }

    /** Every dependency the project declares. */
    public List<Coordinates> dependencies() {
        return coordinates("dependencies", "dependency");
    }

    /** Every build plugin the project declares. */
    public List<Coordinates> plugins() {
        return child(project, "build")
                .map(build -> under(build, "plugins", "plugin"))
                .orElse(List.of());
    }

    /** The configuration value of a plugin, by artifact and element name. */
    public Optional<String> pluginSetting(String artifactId, String setting) {
        return plugins().stream()
                .filter(plugin -> plugin.artifactId().equals(artifactId))
                .findFirst()
                .flatMap(plugin -> child(plugin.element(), "configuration"))
                .flatMap(configuration -> child(configuration, setting))
                .map(Node::getTextContent)
                .map(String::strip);
    }

    /** A dependency or plugin: its coordinates, its version as written, and its element. */
    public record Coordinates(String groupId, String artifactId, Optional<String> version, Element element) {

        /** The version with any property it names filled in from the build. */
        public Optional<String> resolvedIn(BuildFile build) {
            return version.map(written -> {
                if (written.startsWith("${") && written.endsWith("}")) {
                    return build.property(written.substring(2, written.length() - 1))
                            .orElse(written);
                }
                return written;
            });
        }
    }

    private List<Coordinates> coordinates(String list, String item) {
        return under(project, list, item);
    }

    private static List<Coordinates> under(Element parent, String list, String item) {
        List<Coordinates> found = new ArrayList<>();
        child(parent, list).ifPresent(container -> {
            NodeList children = container.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element element
                        && element.getTagName().equals(item)) {
                    found.add(new Coordinates(
                            child(element, "groupId").map(Node::getTextContent).orElse("org.apache.maven.plugins"),
                            child(element, "artifactId")
                                    .map(Node::getTextContent)
                                    .orElseThrow(),
                            child(element, "version").map(Node::getTextContent).map(String::strip),
                            element));
                }
            }
        });
        return List.copyOf(found);
    }

    private static Optional<Element> child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element
                    && element.getTagName().equals(name)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }
}
