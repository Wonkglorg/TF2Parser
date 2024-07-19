package com.Wonkglorg.util;

import org.wonkglorg.files.readwrite.TxtFileUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * Creates a new DataObject from a list of strings if they match a valid format as tf2 layed it out (new lines matter unlike json)
     *
     * @param content the content to parse
     * @return a new DataObject
     */
    public static TFDataObject from(List<String> content) {
        return new TFDataObject("", "", content);
    }

    /**
     * Creates a new DataObject from a file
     *
     * @param path    the path to the file
     * @param charset the charset to use
     * @return a new DataObject
     */
    public static TFDataObject from(Path path, Charset charset) {
        return new TFDataObject("", "", TxtFileUtil.readFromFile(path.toFile(), charset));
    }

    /**
     * Creates a new DataObject from a file
     *
     * @param path    the path to the file
     * @param charset the charset to use
     * @return a new DataObject
     */
    public static TFDataObject from(String path, Charset charset) {
        return new TFDataObject("", "", TxtFileUtil.readFromFile(path, charset));
    }

    /**
     * Merges multiple DataObjects into one where the returned object represents a new root with updated sub paths matching the new structure
     *
     * @param objects the objects to merge
     * @return a new DataObject
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
            merged.contentMap.put(basePath, object);
        } else {
            for (var entry : object.contentMap.entrySet()) {
                String key = entry.getKey();
                TFDataObject value = entry.getValue();
                String newPath = basePath.isEmpty() ? key : basePath + "." + key;
                if (merged.contentMap.containsKey(newPath)) {
                    mergeRecursive(value, newPath, merged);
                } else {
                    merged.contentMap.put(newPath, value);
                }
            }
        }
    }

    /**
     * Merges multiple DataObjects into one where the returned object represents a new root with updated sub paths matching the new structure
     *
     * @param objects the objects to merge
     * @return a new DataObject
     */

    public static TFDataObject merge(List<TFDataObject> objects) {
        return merge(objects.toArray(new TFDataObject[0]));
    }

    //matches any word enclosed in quotes
    private static final Pattern keyWordPattern = Pattern.compile("\"([^\"]+)\"");


    //todo perhaps make it more robust in the way it is split up? or not yml is also very strict no need to overcomplicate
    //todo for the love of god do not ever ever ever touch this again! it is working and I don't want to break it
    private void parse(List<String> content, String parentPath) {
        if (content == null || content.isEmpty()) {
            return;
        }
        int depth = 0;
        String baseKey = null;
        String key = null;
        //keeps track of all the firest level children and stores their content
        Map<String, List<String>> childContentMap = new HashMap<>();
        //currently only used with 1 value being the base key but used to have a different purpose staying if ever needed again
        Deque<String> pathStack = new ArrayDeque<>();
        for (String line : content) {
            line = line.trim().replace("\t", "");
            if (line.isEmpty()) continue;

            if (depth == 1) {
                Matcher matcher = keyWordPattern.matcher(line);
                if (matcher.find()) {
                    String newKey = matcher.group(1);
                    if (!matcher.find()) {
                        if (key == null) {
                            key = newKey;
                            //only gets invoked if there is not already a current child collecting values, should not be the case usually but some edgecases needed it.
                            //the key gets freed when the next inline is a normal value after a child has been closed
                            addStringElementToMap(key, line, childContentMap);
                        }
                        //if the key has a value associated with it, it is added to the current map and not indented to the children
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
                //means nothing should be lefz and we can clear it all outif something comes after it,
                //then the key will be overwritten but should potentially still work fine but that is not intended bvahavior
                if (depth == 0) {
                    childContentMap.clear();
                    pathStack.pop();
                    continue;
                }
                //this is done because I needed to keep track of the values right under the root to further recurse
                if (depth == 1) {
                    String newPath = parentPath;
                    newPath = newPath.isEmpty() ? baseKey : parentPath + "." + baseKey;
                    //adds the final } line to the map otherwise its mismatched formatting
                    addStringElementToMap(key, line, childContentMap);
                    TFDataObject dataObject = new TFDataObject(newPath, key, childContentMap.get(key));
                    contentMap.put(key, dataObject);
                    childContentMap.clear();
                    key = null;
                    continue;
                }
            }
            if (depth == 0) {
                Matcher matcher = keyWordPattern.matcher(line);
                if (matcher.find()) {
                    String tempKey = matcher.group(1);
                    //if this check passes we have a value to out key not a nested object
                    if (matcher.find()) {
                        key = tempKey;
                        String value = matcher.group(1);
                        TFDataObject dataObject = new TFDataObject(path, key, value);
                        contentMap.put(key, dataObject);
                    } else {
                        //should only ever happen once, if not then the file is not formatted correctly and any errors should not be dealt with on my end
                        baseKey = tempKey;
                    }
                }
                continue;
            }

            //anything after the first 2 levels (1 is the base level, 2 is the first nesting) will be stored and handled recursivly
            if (depth >= 2) {
                addStringElementToMap(key, line, childContentMap);
            }
        }

        this.key = baseKey;
    }

    private void addStringElementToMap(String key, String value, Map<String, List<String>> map) {
        var list = map.getOrDefault(key, new ArrayList<>());
        list.add(value);
        map.put(key, list);
    }


    //todo change this to actually make sense? create subpaths until this matches the correct structure?

    /**
     * Adds a new key value pair to the data object
     *
     * @param path  the path to add the key value pair to
     * @param key   the key
     * @param value the value
     */
    public void add(String path, String key, String value) {
        String[] paths = path.split("\\.");

        if (paths.length == 0) {
            contentMap.put(key, new TFDataObject("", key, value));
            return;
        }

        recursiveAdd(paths, key, value, 0, this, "");
    }

    private void recursiveAdd(String[] paths, String key, String value, int depth, TFDataObject dataObject, String currentPath) {
        if (depth == paths.length) {
            dataObject.getContentMap().put(key, new TFDataObject(currentPath, key, value));
            return;
        }

        String currentKey = paths[depth];
        String newPath = currentPath.isEmpty() ? currentKey : currentPath + "." + currentKey;

        if (!dataObject.getContentMap().containsKey(currentKey)) {
            dataObject.getContentMap().put(currentKey, new TFDataObject(newPath, currentKey, (String) null));
        }

        recursiveAdd(paths, key, value, depth + 1, dataObject.getContentMap().get(currentKey), newPath);
    }

    /**
     * Gets all children with 0 depth from the current data object
     *
     * @return
     */
    public Set<String> getChildren() {
        if (contentMap.isEmpty() || isValue) {
            return Set.of();
        }
        return contentMap.keySet();
    }

    /**
     * Gets a child from the current data object, can be a path to any object below it.
     *
     * @param path
     * @return
     */
    public TFDataObject get(String path) {
        return getSubPath(contentMap, path.split("\\."));
    }

    /**
     * Checks if the current data object contains a key
     *
     * @param path
     * @return
     */
    public boolean containsKey(String path) {
        return getSubPath(contentMap, path.split("\\.")) != null;
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


    /**
     * Gets the current objects key
     *
     * @return
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets a value from the specified path by its key
     *
     * @param path
     * @param key
     * @return
     */
    public String getValue(String path, String key) {

        TFDataObject dataObject = getSubPath(contentMap, path.split("\\."));
        if (dataObject == null) {
            return null;
        }
        return dataObject.get(key).getValue();
    }

    /**
     * Gets the current objects value
     *
     * @param defaultValue the default value to return if no value is present
     * @return the value
     */
    public String getValue(String defaultValue) {
        if (isValue) {
            if (value == null) {
                return defaultValue;
            }
            return value;
        }
        return defaultValue;
    }

    /**
     * @return the associated value of the current object or null
     */
    public String getValue() {
        if (isValue) return value;
        return null;
    }

    /**
     * Gets a value from the specified path by its key
     *
     * @param path         the path to the value
     * @param key          the key of the value
     * @param defaultValue the default value to return if no value is present
     * @return
     */
    public String getValue(String path, String key, String defaultValue) {
        TFDataObject dataObject = getSubPath(contentMap, path.split("\\."));
        if (dataObject == null) {
            return defaultValue;
        }
        return dataObject.get(key).getValue(defaultValue);
    }

    private Map<String, TFDataObject> getContentMap() {
        return contentMap;
    }


    //todo create immuteable map copy


    /**
     * Converts the data object to a json string
     *
     * @return the json string
     */
    public String toJson() {
        StringBuilder builder = new StringBuilder();
        toJson(builder);
        return builder.toString();
    }

    /**
     * Helper method to convert the data object to a json string
     *
     * @param builder the StringBuilder to append to
     */
    private void toJson(StringBuilder builder) {
        builder.append("{\n");
        int size = contentMap.size();
        int count = 0;
        for (Map.Entry<String, TFDataObject> entry : contentMap.entrySet()) {
            count++;
            builder.append("\"").append(escapeJson(entry.getKey())).append("\" : ");
            TFDataObject dataObject = entry.getValue();
            if (!dataObject.isValue) {
                dataObject.toJson(builder);
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
    }

    /**
     * Converts the data object to a YAML string
     *
     * @param indentAmount how many spaces an indent should be
     * @return the YAML string
     */
    public String toYaml(int indentAmount) {
        StringBuilder builder = new StringBuilder();
        toYaml(builder, 0, " ".repeat(indentAmount));
        return builder.toString();
    }

    private void toYaml(StringBuilder builder, int indentLevel, String indentAmount) {
        String indent = indentAmount.repeat(indentLevel);
        Map<String, List<TFDataObject>> mergedContentMap = mergeDuplicates(contentMap);

        for (Map.Entry<String, List<TFDataObject>> entry : mergedContentMap.entrySet()) {
            String key = entry.getKey();
            List<TFDataObject> dataObjects = entry.getValue();

            // Quote the key if it contains special characters
            builder.append(indent).append(quoteYaml(key)).append(":");

            if (dataObjects.size() > 1) {
                // Handle merging multiple data objects with the same key
                builder.append("\n");
                for (TFDataObject dataObject : dataObjects) {
                    if (!dataObject.isValue) {
                        dataObject.toYaml(builder, indentLevel + 1, indentAmount);
                    } else {
                        builder.append(indentAmount.repeat(indentLevel + 1))
                                .append("- \"").append(escapeYaml(dataObject.value)).append("\"\n");
                    }
                }
            } else {
                // Single data object case
                TFDataObject dataObject = dataObjects.get(0);
                if (!dataObject.isValue) {
                    builder.append("\n");
                    dataObject.toYaml(builder, indentLevel + 1, indentAmount);
                } else {
                    builder.append(" \"").append(escapeYaml(dataObject.value)).append("\"\n");
                }
            }
        }
    }

    private Map<String, List<TFDataObject>> mergeDuplicates(Map<String, TFDataObject> originalMap) {
        Map<String, List<TFDataObject>> mergedMap = new LinkedHashMap<>();
        for (Map.Entry<String, TFDataObject> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            TFDataObject value = entry.getValue();
            mergedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return mergedMap;
    }

    private String quoteYaml(String key) {
        if (key == null) return "";
        // Quote the key if it contains special characters
        return key.contains(":") || key.contains(" ") || key.contains("\"") ? "\"" + key + "\"" : key;
    }

    private String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");  // Escape quotes inside values
    }

    // Example methods for initialization
    public void setValue(String value) {
        this.isValue = true;
        this.value = value;
    }

    public void addContent(String key, TFDataObject value) {
        this.isValue = false;
        this.contentMap.put(key, value);
    }


    /**
     * Writes the json representation to a file
     *
     * @param file the file to save to
     * @throws IOException
     */
    public void writeJsonToFile(String directory, String file) throws IOException {
        Path path = Paths.get(directory, file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson());
    }

    /**
     * Writes the yaml representation to a file
     *
     * @param file the file to save to
     * @throws IOException
     */
    public void writeYamlToFile(String directory, String file, int indentAmount) throws IOException {
        Path path = Paths.get(directory, file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, toYaml(indentAmount));
    }


    /**
     * //todo test if this all works
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

    /**
     * Returns all key values in the data object as a list of DataObjectEntry
     *
     * @param object     the object to search in
     * @param path       the path to the object
     * @param searchPath the path to search for, if null all key values are returned
     * @param depth      the depth to search for, if -1 all key values are returned
     * @return a list of DataObjectEntry
     */
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

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return contentMap.toString();
    }

    //-------records and data classes ---------

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
