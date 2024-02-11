package com.Wonkglorg.util;

import java.util.List;

public class TFDataVisualizer {

    private final TFDataObject dataObject;

    public TFDataVisualizer(TFDataObject dataObject) {
        this.dataObject = dataObject;
    }

    public void printFormatted(String string, String format, int depth) {
        printFormatted(string, format, depth, 0, 0, 0);
    }

    public void printFormatted(String path, String format, int depth, int pathWidth, int keyWidth, int valueWidth) {
        List<TFDataObject.DataObjectEntry> entries = dataObject.getKeyValues(path, depth);

        if (pathWidth == 0)
            pathWidth = entries.stream().mapToInt(e -> (dataObject.getKey() + "." + e.path()).length()).max().orElse(0);
        if (keyWidth == 0) keyWidth = entries.stream().mapToInt(e -> e.key().length()).max().orElse(0);
        if (valueWidth == 0) valueWidth = entries.stream().mapToInt(e -> e.value().length()).max().orElse(0);

        pathWidth += 2;
        keyWidth += 2;
        valueWidth += 2;

        if (format == null) {
            format = "|%path | %key | %value |";
        }

        System.out.println(formatTitle(format, keyWidth, valueWidth, pathWidth));
        System.out.println("_".repeat(pathWidth + keyWidth + valueWidth + 9));

        for (TFDataObject.DataObjectEntry entry : entries) {
            String p = formatString(dataObject.getKey() + "." + entry.path(), pathWidth);
            String k = formatString(entry.key(), keyWidth);
            String v = formatString(entry.value(), valueWidth);
            String output = format.replace("%path", p).replace("%key", k).replace("%value", v);
            System.out.println(output);
        }

    }

    public void printFormatted(String path, int depth) {
        String format = "| %path | %key | %value |";
        printFormatted(path, format, depth, 0, 0, 0);
    }

    public void printFormatted() {
        printFormatted(null, -1);
    }

    /*
    public void printFormatted(String path) {
        printFormatted(path, -1);
    }


     */
    private String formatTitle(String format, int keyWidth, int valueWidth, int pathWidth) {
        String key = formatString("Key", keyWidth);
        String value = formatString("Value", valueWidth);
        String pathString = formatString("Path", pathWidth);
        return format.replace("%path", pathString).replace("%key", key).replace("%value", value);
    }

    private String formatString(String input, int width) {
        if (input.length() < width) {
            return String.format("%-" + width + "s", input);
        } else {
            return input.substring(0, width);
        }
    }


    public void printUniquePaths(String string, int depth) {
        List<String> uniquePaths = dataObject.getAllUniquePaths(null, depth);
        int maxWidth = uniquePaths.stream().mapToInt(String::length).max().orElse(0);
        for (String currentPath : uniquePaths) {
            System.out.println(formatString(currentPath, maxWidth));
        }
    }

    public void printUniquePaths(String string) {
        printUniquePaths(string, -1);
    }

    public void printUniquePaths() {
        printUniquePaths(null, -1);
    }

}
