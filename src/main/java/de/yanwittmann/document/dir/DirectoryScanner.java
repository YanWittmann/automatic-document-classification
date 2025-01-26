package de.yanwittmann.document.dir;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DirectoryScanner {
    private final DirectoryNode root;

    public DirectoryScanner(String rootPath) throws IOException {
        File rootDir = new File(rootPath);
        this.root = buildDirectoryTree(null, rootDir);
    }

    private DirectoryNode buildDirectoryTree(DirectoryNode parent, File dir) throws IOException {
        DirectoryNode node = new DirectoryNode(parent, dir.getName());
        File[] files = dir.listFiles();
        if (files == null) {
            return node;
        }

        if (new File(dir, ".docinfo").exists()) {
            node.setDocfileContent(FileUtils.readFileToString(new File(dir, ".docinfo"), StandardCharsets.UTF_8));
        }

        if (new File(dir, ".docignore").exists()) {
            return node;
        }

        List<File> directories = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                directories.add(file);
            } else {
                fileList.add(file);
            }
        }

        for (File file : fileList) {
            node.getFiles().add(file.getName());
        }

        for (File subDir : directories) {
            DirectoryNode subNode = buildDirectoryTree(node, subDir);
            node.getSubdirectories().put(subDir.getName(), subNode);
        }

        return node;
    }

    public String getTreeAsString() {
        return getTreeAsString(Integer.MAX_VALUE);
    }

    public String getTreeAsString(int maxFilesPerDir) {
        StringBuilder sb = new StringBuilder();
        sb.append(root.getName()).append("/\n");
        buildTreeString(root, "", sb, maxFilesPerDir);
        return sb.toString();
    }

    private void buildTreeString(DirectoryNode node, String prefix, StringBuilder sb, int maxFilesPerDir) {
        List<String> files = node.getFiles();
        int totalFiles = files.size();
        int filesToShow = Math.min(maxFilesPerDir, totalFiles);
        List<String> displayedFiles = files.subList(0, filesToShow);
        int remainingFiles = totalFiles - filesToShow;

        List<Object> entries = new ArrayList<>();
        entries.addAll(displayedFiles);
        if (remainingFiles > 0) {
            entries.add("(" + remainingFiles + " more files)");
        }
        entries.addAll(node.getSubdirectories().values());

        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            boolean isLast = (i == entries.size() - 1);
            if (entry instanceof String) {
                String entryText = (String) entry;
                sb.append(prefix)
                        .append(isLast ? "└── " : "├── ")
                        .append(entryText)
                        .append("\n");
            } else if (entry instanceof DirectoryNode) {
                DirectoryNode dirEntry = (DirectoryNode) entry;
                sb.append(prefix)
                        .append(isLast ? "└── " : "├── ")
                        .append(dirEntry.getName())
                        .append("/\n");
                String newPrefix = prefix + (isLast ? "    " : "│   ");
                buildTreeString(dirEntry, newPrefix, sb, maxFilesPerDir);
            }
        }
    }

    public JSONObject toShortJson() {
        final Map<String, List<String>> json = new LinkedHashMap<>();

        for (Map.Entry<String, DirectoryNode> entry : root.getSubdirectories().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            json.put(entry.getKey(), new ArrayList<>(entry.getValue().getSubdirectories().keySet()));
        }

        return new JSONObject(json);
    }

    public JSONObject toJson(int maxFilesPerDir, boolean includeFiles) {
        if (maxFilesPerDir < 0) throw new IllegalArgumentException("maxFilesPerDir must be non-negative");
        return buildJson(root, maxFilesPerDir, includeFiles);
    }

    private JSONObject buildJson(DirectoryNode node, int maxFilesPerDir, boolean includeFiles) {
        if (includeFiles) {
            JSONObject json = new JSONObject();

            List<String> files = node.getFiles();
            int totalFiles = files.size();
            int filesToShow = Math.min(maxFilesPerDir, totalFiles);
            int remainingFiles = totalFiles - filesToShow;

            JSONArray filesArray = new JSONArray();
            for (int i = 0; i < filesToShow; i++) {
                filesArray.put(files.get(i));
            }
            if (filesToShow > 0) {
                json.put("files", filesArray);
            }

            JSONObject subdirectories = new JSONObject();
            for (Map.Entry<String, DirectoryNode> entry : node.getSubdirectories().entrySet()) {
                subdirectories.put(entry.getKey(), buildJson(entry.getValue(), maxFilesPerDir, includeFiles));
            }
            json.put("dirs", subdirectories);

            return json;
        } else {
            JSONObject subdirectories = new JSONObject();
            for (Map.Entry<String, DirectoryNode> entry : node.getSubdirectories().entrySet()) {
                subdirectories.put(entry.getKey(), buildJson(entry.getValue(), maxFilesPerDir, includeFiles));
            }
            return subdirectories;
        }

    }

    public Map<String, DirectoryNode> getExampleFiles(int num) {
        final Map<String, DirectoryNode> allFilenames = new LinkedHashMap<>();
        root.recursiveAccess(node -> node.getFiles().forEach(file -> allFilenames.put(file, node)));
        List<Map.Entry<String, DirectoryNode>> entryList = new ArrayList<>(allFilenames.entrySet());
        Collections.shuffle(entryList);
        entryList.removeIf(entry -> !entry.getKey().matches("\\d{4}-\\d{2}-\\d{2}.*"));
        return entryList.stream().limit(num).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<DirectoryNode> getNodesWithDocfileContent() {
        return root.collectNodes(node -> node.getDocfileContent() != null);
    }

    public List<DirectoryNode> getTopLevelDirectories() {
        return new ArrayList<>(root.getSubdirectories().values());
    }

    @Setter
    @Getter
    public static class DirectoryNode {
        private final DirectoryNode parent;
        private final String name;
        private String docfileContent;
        private final List<String> files = new ArrayList<>();
        private final Map<String, DirectoryNode> subdirectories = new LinkedHashMap<>();

        public DirectoryNode(DirectoryNode parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        public String path(boolean omitRoot) {
            if (parent == null) {
                return omitRoot ? "" : name;
            }
            final String parentPath = parent.path(omitRoot);
            return parentPath.isEmpty() ? name : parentPath + "/" + name;
        }

        public void recursiveAccess(Consumer<DirectoryNode> consumer) {
            consumer.accept(this);
            for (DirectoryNode subDir : subdirectories.values()) {
                subDir.recursiveAccess(consumer);
            }
        }

        public List<DirectoryNode> collectNodes(Predicate<DirectoryNode> predicate) {
            List<DirectoryNode> nodes = new ArrayList<>();
            recursiveAccess(node -> {
                if (predicate.test(node)) {
                    nodes.add(node);
                }
            });
            return nodes;
        }
    }
}