package com.Wonkglorg.util;

import org.wonkglorg.files.readwrite.TxtFileUtil;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Represents a data object that can be nested in a json like format original to tf2 files
 * The object can be converted to json or directly accesed through its map
 * Key values are represented as strings, nested objects are represented as DataObjects which can be further accessed
 */
public class TFDataObject {
    private boolean isValue;
    private String value;
    private String key;
    private String path;
    private final Map<String, TFDataObject> contentMap = new HashMap<>();

    private TFDataObject(String path, String key, String value) {
        this.value = value;
        this.key = key;
        this.path = path;
        isValue = value != null;
    }

    private TFDataObject(String path, String key, List<String> content) {
        this.path = path;
        this.key = key;
        isValue = false;
        parse(content, path);
    }

    /**
     * Creates a new DataObject from a key value pair
     *
     * @param content
     * @return
     */
    public static TFDataObject from(List<String> content) {
        return new TFDataObject("", "", content);
    }

    public static TFDataObject from(Path path, Charset charset) {
        return new TFDataObject("", "", TxtFileUtil.readFromFile(path.toFile(), charset));
    }

    public static TFDataObject from(String path, Charset charset) {
        return new TFDataObject("", "", TxtFileUtil.readFromFile(path, charset));
    }

    /**
     * Merges multiple DataObjects into one where the returned object represents a new root
     *
     * @param objects
     * @return
     */
    public static TFDataObject merge(TFDataObject... objects) {
        TFDataObject merged = TFDataObject.from(new ArrayList<>());
        for (TFDataObject object : objects) {
            if (object == null) continue;

            mergeRecursive(object, "", merged);
        }
        return merged;
    }

    private static void mergeRecursive(TFDataObject object, String basePath, TFDataObject merged) {
        if (object.isValue) {
            // If it's a single value, merge with the current base path
            merged.contentMap.put(basePath, object);
        } else {
            // If it's a nested map, merge recursively with updated paths
            for (var entry : object.contentMap.entrySet()) {
                String key = entry.getKey();
                TFDataObject value = entry.getValue();
                String newPath = basePath.isEmpty() ? key : basePath + "." + key;
                if (merged.contentMap.containsKey(newPath)) {
                    // If the key already exists, merge recursively
                    mergeRecursive(value, newPath, merged);
                } else {
                    // Otherwise, add the new entry directly
                    merged.contentMap.put(newPath, value);
                }
            }
        }
    }

    public static TFDataObject merge(List<TFDataObject> objects) {
        return merge(objects.toArray(new TFDataObject[0]));
    }

    private static final Pattern pattern = Pattern.compile("\"([^\"]+)\"");


    //todo for the love of god do not ever ever ever touch this again! it is working and I don't want to break it
    private void parse(List<String> content, String parentPath) {
        if (content == null || content.isEmpty()) {
            return;
        }
        int depth = 0;
        String baseKey = null;
        String key = null;
        Map<String, List<String>> childContentMap = new HashMap<>();
        Deque<String> pathStack = new ArrayDeque<>();
        for (String line : content) {
            line = line.trim().replace("\t", "");
            if (line.isEmpty()) continue;
            if (depth == 1) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String newKey = matcher.group(1);
                    if (!matcher.find()) {
                        if (key == null) {
                            key = newKey;
                            addStringElementToMap(key, line, childContentMap);
                        }
                    } else {
                        String value = matcher.group(1);
                        String newPath = String.join(".", pathStack);
                        TFDataObject dataObject = new TFDataObject(newPath, newKey, value);
                        contentMap.put(newKey, dataObject);
                        key = null;
                    }

                }
            }
            if (line.startsWith("{")) {
                depth++;
                if (depth == 1) {
                    pathStack.push(baseKey);
                    continue;
                }
            }
            if (line.startsWith("}")) {
                depth--;
                if (depth == 0) {
                    childContentMap.clear();
                    pathStack.pop();
                    continue;
                }
                if (depth == 1) {
                    String newPath = parentPath;
                    newPath = newPath.isEmpty() ? baseKey : parentPath + "." + baseKey;
                    addStringElementToMap(key, line, childContentMap);
                    TFDataObject dataObject = new TFDataObject(newPath, key, childContentMap.get(key));
                    contentMap.put(key, dataObject);
                    childContentMap.clear();
                    key = null;
                    continue;
                }
            }
            if (depth == 0) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String tempKey = matcher.group(1);
                    if (matcher.find()) {
                        key = tempKey;
                        String value = matcher.group(1);
                        TFDataObject dataObject = new TFDataObject(path, key, value);
                        contentMap.put(key, dataObject);
                    } else {
                        baseKey = tempKey;
                    }
                }
                continue;
            }
            if (depth >= 2) {
                addStringElementToMap(key, line, childContentMap);
            }
        }
    }

    private void addStringElementToMap(String key, String value, Map<String, List<String>> map) {
        var list = map.getOrDefault(key, new ArrayList<>());
        list.add(value);
        map.put(key, list);
    }


    public void add(String path, String key, String value) {
        contentMap.put(key, new TFDataObject(path, key, value));
    }

    public Set<String> getChildren() {
        if (contentMap.isEmpty() || isValue) {
            return Set.of();
        }
        return contentMap.keySet();
    }

    public TFDataObject get(String key) {
        return getSubPath(contentMap, key.split("\\."));
    }

    public boolean containsKey(String key) {
        return getSubPath(contentMap, key.split("\\.")) != null;
    }

    private TFDataObject getSubPath(Map<String, TFDataObject> dataMap, String[] keys) {
        if (keys.length == 0 || dataMap == null) {
            return null;
        }
        String currentKey = keys[0];
        TFDataObject childObject = dataMap.get(currentKey);

        if (keys.length == 1) {
            return childObject;
        }

        return getSubPath((childObject).getContentMap(), Arrays.copyOfRange(keys, 1, keys.length));
    }


    public String getKey() {
        return key;
    }

    public String getValue(String path, String key) {

        TFDataObject dataObject = getSubPath(contentMap, path.split("\\."));
        if (dataObject == null) {
            return null;
        }
        return dataObject.get(key).getValue();
    }

    public String getValue(String defaultValue) {
        if (isValue) {
            if (value == null) {
                return defaultValue;
            }
            return value;
        }
        return defaultValue;
    }

    public String getValue(String path, String key, String defaultValue) {
        TFDataObject dataObject = getSubPath(contentMap, path.split("\\."));
        if (dataObject == null) {
            return defaultValue;
        }
        return dataObject.get(key).getValue(defaultValue);
    }

    public String get(String key, String defaultValue) {
        TFDataObject dataObject = contentMap.get(key);
        if (dataObject == null) {
            return defaultValue;
        }
        return dataObject.getValue();
    }


    public Map<String, TFDataObject> getContentMap() {
        return contentMap;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        int size = contentMap.size();
        int count = 0;
        for (Map.Entry<String, TFDataObject> entry : contentMap.entrySet()) {
            count++;
            builder.append("\"").append(escapeJson(entry.getKey())).append("\" : ");
            TFDataObject dataObject = entry.getValue();
            if (!dataObject.isValue) {
                builder.append(dataObject.toJson());
            } else {
                builder.append("\"").append(escapeJson(dataObject.value)).append("\"");
            }
            if (count < size) {
                builder.append(",\n");
            } else {
                builder.append("\n");
            }
        }
        builder.append("}");
        return builder.toString();
    }


    public String getValue() {
        if (isValue) return value;
        return null;
    }


    /**
     * Returns all key values in the data object as a list of DataObjectEntry
     *
     * @param searchPath the path to search for, if null all key values are returned
     * @return a list of DataObjectEntry
     */
    public List<DataObjectEntry> getKeyValues(String searchPath, int depth) {
        List<DataObjectEntry> entries = new ArrayList<>();
        for (var entry : contentMap.entrySet()) {
            String key = entry.getKey();
            TFDataObject value = entry.getValue();
            if (searchPath == null || value.getPath().startsWith(searchPath)) { // Check if value's path starts with searchPath
                if (!value.isValue()) {
                    if (depth == 0) continue;
                    entries.addAll(getNestedKeyValues(value, key, searchPath, depth));
                } else {
                    entries.add(new DataObjectEntry(value.getPath(), key, value.getValue()));
                }
            }
        }
        return entries;
    }

    private List<DataObjectEntry> getNestedKeyValues(TFDataObject object, String path, String searchPath, int depth) {
        List<DataObjectEntry> nestedEntries = new ArrayList<>();
        if (depth == 0) return nestedEntries;

        for (var entry : object.getContentMap().entrySet()) {
            String key = entry.getKey();
            TFDataObject value = entry.getValue();
            String fullPath = path.isEmpty() ? "" : path;
            if (searchPath == null || value.getPath().startsWith(searchPath)) { // Check if value's path starts with searchPath
                if (!value.isValue()) {
                    fullPath = fullPath.isEmpty() ? key : fullPath + "." + key;
                    nestedEntries.addAll(getNestedKeyValues(value, fullPath, searchPath, depth - 1));
                } else {

                    nestedEntries.add(new DataObjectEntry(fullPath, key, value.getValue()));
                }
            }
        }

        return nestedEntries;
    }

    /**
     * Returns all unique paths in the data object as a list of strings starting from the given path
     *
     * @param startingPath the path to start from, if null all paths are returned
     * @param depth        the depth to search for, if -1 all paths are returned
     * @return a list of unique paths
     */
    public List<String> getAllUniquePaths(String startingPath, int depth) {
        List<String> uniquePaths = new ArrayList<>();
        for (var entry : contentMap.entrySet()) {
            String key = entry.getKey();
            TFDataObject dataObject = entry.getValue();
            if (!dataObject.isValue) {
                uniquePaths.addAll(getNestedPaths(dataObject, key, startingPath, depth));
            } else if (startingPath == null || key.startsWith(startingPath)) {
                uniquePaths.add(key);
            }
        }
        return uniquePaths;
    }

    private List<String> getNestedPaths(TFDataObject object, String path, String startingPath, int depth) {
        List<String> nestedPaths = new ArrayList<>();
        if (depth == 0) return nestedPaths;

        for (var entry : object.getContentMap().entrySet()) {
            String key = entry.getKey();
            TFDataObject value = entry.getValue();
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (startingPath == null || fullPath.startsWith(startingPath)) {
                nestedPaths.add(fullPath);
                if (!isValue) {
                    nestedPaths.addAll(getNestedPaths(value, fullPath, startingPath, depth - 1));
                }
            }

        }
        return nestedPaths;
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatKeyValue(String input) {
        return input.replaceAll("^\"|\"$", "");
    }

    public String getPath() {
        return path;
    }

    public void setValue(String value) {
        this.value = value;
        isValue = value != null;

    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return contentMap.toString();
    }

    private record ValueCheck(String key, String value) {
        public boolean hasKey() {
            return key != null;
        }

        public boolean hasValue() {
            return value != null;
        }
    }

    public class DataObjectEntry {

        private final String path;
        private final String key;
        private final String value;

        public DataObjectEntry(String path, String key, String value) {
            this.key = key;
            this.value = value;
            this.path = path;
        }

        public String path() {
            return path;
        }

        public String key() {
            return key;
        }

        public String value() {
            return value;
        }

    }

    public boolean isValue() {
        return isValue;
    }
}
