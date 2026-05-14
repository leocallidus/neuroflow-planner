package com.example.neuroflowplanner.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal SVG loader for project-owned icon assets.
 *
 * <p>Supports a small subset: {@code <path>}, {@code <rect>}, {@code <circle>} with
 * {@code fill}, {@code stroke}, {@code stroke-width}, and {@code viewBox}.</p>
 */
public final class SvgIcon {

    private static final Pattern VIEWBOX = Pattern.compile("viewBox\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TAG_SUPPORTED = Pattern.compile("<(rect|circle|path)\\b([^>]*)/?>");
    private static final Pattern ATTR = Pattern.compile("(\\w[\\w-]*)\\s*=\\s*\"([^\"]*)\"");

    private static final Map<String, Template> CACHE = new ConcurrentHashMap<>();

    private SvgIcon() {}

    public static Node load(Class<?> resourceBase, String resourcePath, double sizePx) {
        Template template = CACHE.computeIfAbsent(resourcePath, path -> parseTemplate(resourceBase, path));
        return template.build(sizePx);
    }

    private static Template parseTemplate(Class<?> resourceBase, String resourcePath) {
        String svg = readResource(resourceBase, resourcePath);
        ViewBox viewBox = parseViewBox(svg);
        List<Element> elements = new ArrayList<>();

        Matcher tags = TAG_SUPPORTED.matcher(svg);
        while (tags.find()) {
            String tagName = tags.group(1);
            Map<String, String> attributes = parseAttributes(tags.group(2));
            switch (tagName) {
                case "rect" -> elements.add(Element.rect(attributes));
                case "circle" -> elements.add(Element.circle(attributes));
                case "path" -> elements.add(Element.path(attributes));
                default -> {
                }
            }
        }

        return new Template(viewBox, elements);
    }

    private static String readResource(Class<?> resourceBase, String resourcePath) {
        try (InputStream in = resourceBase.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("SVG resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read SVG resource: " + resourcePath, e);
        }
    }

    private static ViewBox parseViewBox(String svg) {
        Matcher m = VIEWBOX.matcher(svg);
        if (!m.find()) {
            return new ViewBox(0, 0, 64, 64);
        }
        String[] parts = m.group(1).trim().split("\\s+");
        if (parts.length != 4) {
            return new ViewBox(0, 0, 64, 64);
        }
        return new ViewBox(
            parseDouble(parts[0], 0),
            parseDouble(parts[1], 0),
            Math.max(1, parseDouble(parts[2], 64)),
            Math.max(1, parseDouble(parts[3], 64))
        );
    }

    private static Map<String, String> parseAttributes(String raw) {
        java.util.HashMap<String, String> attrs = new java.util.HashMap<>();
        if (raw == null || raw.isBlank()) {
            return attrs;
        }
        Matcher m = ATTR.matcher(raw);
        while (m.find()) {
            attrs.put(m.group(1), m.group(2));
        }
        return attrs;
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Paint parsePaint(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Paint.valueOf(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ViewBox(double minX, double minY, double width, double height) {}

    private record Template(ViewBox viewBox, List<Element> elements) {
        Node build(double sizePx) {
            Group g = new Group();
            Rectangle bounds = new Rectangle(viewBox.minX(), viewBox.minY(), viewBox.width(), viewBox.height());
            bounds.setFill(Color.TRANSPARENT);
            bounds.setMouseTransparent(true);
            g.getChildren().add(bounds);
            for (Element el : elements) {
                Node node = el.toNode();
                if (node != null) {
                    g.getChildren().add(node);
                }
            }

            double scale = sizePx / Math.max(viewBox.width(), viewBox.height());
            g.getTransforms().addAll(
                new Translate(-viewBox.minX(), -viewBox.minY()),
                new Scale(scale, scale)
            );
            g.setMouseTransparent(true);
            return g;
        }
    }

    private sealed interface Element permits Element.RectEl, Element.CircleEl, Element.PathEl {
        Node toNode();

        static Element rect(Map<String, String> attrs) {
            return new RectEl(attrs);
        }

        static Element circle(Map<String, String> attrs) {
            return new CircleEl(attrs);
        }

        static Element path(Map<String, String> attrs) {
            return new PathEl(attrs);
        }

        record RectEl(Map<String, String> attrs) implements Element {
            @Override
            public Node toNode() {
                double x = parseDouble(attrs.get("x"), 0);
                double y = parseDouble(attrs.get("y"), 0);
                double w = parseDouble(attrs.get("width"), 0);
                double h = parseDouble(attrs.get("height"), 0);
                double rx = parseDouble(attrs.get("rx"), 0);
                double ry = parseDouble(attrs.get("ry"), rx);
                if (w <= 0 || h <= 0) {
                    return null;
                }
                Rectangle r = new Rectangle(x, y, w, h);
                if (rx > 0 || ry > 0) {
                    r.setArcWidth(rx * 2);
                    r.setArcHeight(ry * 2);
                }
                Paint fill = parsePaint(attrs.get("fill"));
                if (fill != null) {
                    r.setFill(fill);
                }
                Paint stroke = parsePaint(attrs.get("stroke"));
                if (stroke != null) {
                    r.setStroke(stroke);
                    r.setStrokeWidth(parseDouble(attrs.get("stroke-width"), 1));
                }
                return r;
            }
        }

        record CircleEl(Map<String, String> attrs) implements Element {
            @Override
            public Node toNode() {
                double cx = parseDouble(attrs.get("cx"), 0);
                double cy = parseDouble(attrs.get("cy"), 0);
                double r = parseDouble(attrs.get("r"), 0);
                if (r <= 0) {
                    return null;
                }
                Circle c = new Circle(cx, cy, r);
                Paint fill = parsePaint(attrs.get("fill"));
                if (fill != null) {
                    c.setFill(fill);
                }
                Paint stroke = parsePaint(attrs.get("stroke"));
                if (stroke != null) {
                    c.setStroke(stroke);
                    c.setStrokeWidth(parseDouble(attrs.get("stroke-width"), 1));
                }
                return c;
            }
        }

        record PathEl(Map<String, String> attrs) implements Element {
            @Override
            public Node toNode() {
                String d = attrs.get("d");
                if (d == null || d.isBlank()) {
                    return null;
                }
                SVGPath p = new SVGPath();
                p.setContent(d);
                Paint fill = parsePaint(attrs.get("fill"));
                if (fill != null) {
                    p.setFill(fill);
                }
                Paint stroke = parsePaint(attrs.get("stroke"));
                if (stroke != null) {
                    p.setStroke(stroke);
                    p.setStrokeWidth(parseDouble(attrs.get("stroke-width"), 1));
                }
                return p;
            }
        }
    }
}
