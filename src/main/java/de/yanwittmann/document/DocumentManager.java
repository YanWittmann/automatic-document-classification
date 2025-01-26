package de.yanwittmann.document;

import de.yanwittmann.document.ai.ChatUtil;
import de.yanwittmann.document.ai.CompletionClient;
import de.yanwittmann.document.dir.DirectoryScanner;
import de.yanwittmann.document.dir.FileMover;
import de.yanwittmann.document.model.Config;
import de.yanwittmann.document.model.DFileCategorization;
import de.yanwittmann.document.pdf.OCRProcessor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DocumentManager {

    private final DirectoryScanner scanner;
    private final CompletionClient aiClient;
    private final FileMover fileMover;

    public DocumentManager() throws IOException {
        this.scanner = new DirectoryScanner(Config.Props.DOCUMENTS_REF_DIR_BASEPATH.get());
        this.aiClient = CompletionClient.builder()
                .baseUrl(Config.Props.AI_CHAT_BASEURL.get())
                .model(Config.Props.AI_CHAT_MODEL.get())
                .build();
        this.fileMover = new FileMover(Config.Props.DOCUMENTS_MOVE_DIR_BASEPATH.get());
    }

    public static void main(String[] args) throws IOException {
        final DocumentManager documentManager = new DocumentManager();

        if (args.length == 0) {
            System.err.println("No files provided.");
            return;
        }

        for (int i = 0, argsLength = args.length; i < argsLength; i++) {
            final String filePath = args[i];
            final File processFile = new File(filePath);
            System.out.println("[" + (i + 1) + " / " + args.length + "] " + processFile.getName());

            final DFileCategorization fileCategorization;
            try {
                fileCategorization = documentManager.categorizeFile(processFile);
            } catch (Exception e) {
                System.err.println("Error processing " + filePath + ": " + e.getMessage());
                e.printStackTrace();
                continue;
            }

            final DFileCategorization finalFileCategorization = fileCategorization.retype(processFile.getName());
            System.out.println("[" + (i + 1) + " / " + args.length + "] moving to: " + finalFileCategorization);
            documentManager.fileMover.moveFile(processFile, finalFileCategorization);
        }
    }

    private String getCurrentDate() {
        return LocalDate.now().toString();
    }

    private DFileCategorization categorizeFile(File file) throws Exception {
        final OCRProcessor ocr = new OCRProcessor();
        final String ocrText = ocr.cleanOcrResult(ocr.processFile(file), 2000);

        final JSONArray exampleFiles = new JSONArray();
        for (Map.Entry<String, DirectoryScanner.DirectoryNode> entry : scanner.getExampleFiles(4).entrySet()) {
            exampleFiles.put(new JSONObject()
                    .put("path", entry.getValue().path(true))
                    .put("filename", entry.getKey())
            );
        }

        final Map<String, String> promptParameters = new HashMap<>(Map.of(
                "ocr_text", ocrText,
                // "directory_structure", scanner.toJson(3, false).toString(1),
                "directory_structure", scanner.toShortJson().toString(1),
                "docfiles", scanner.getNodesWithDocfileContent().stream().map(n -> n.path(true) + " --> " + n.getDocfileContent()).collect(Collectors.joining("\n")),
                "example_filenames", exampleFiles.toString(1),
                "current_date", getCurrentDate(),
                "top_level_directories", new JSONArray(scanner.getTopLevelDirectories().stream().map(DirectoryScanner.DirectoryNode::getName).toList()).toString()
        ));

        {
            final String prompt = print(ChatUtil.fillTemplateFromClasspath("chat/summarize-file-01.txt", promptParameters));
            final String completion = print(ChatUtil.filterThinking(aiClient.generateTextCompletion(prompt)));

            promptParameters.put("ocr_summary", completion);
        }

        {
            final String prompt = print(ChatUtil.fillTemplateFromClasspath("chat/classify-file-02.txt", promptParameters));
            final String completion = print(aiClient.generateTextCompletion(prompt));
            final JSONObject completionJson = ChatUtil.extractJsonObject(completion);
            if (completionJson == null) {
                throw new RuntimeException("No valid JSON response found in completion: " + completion.replace("\n", "\\n"));
            }

            return DFileCategorization.fromJson(completionJson);
        }
    }

    private static <T> T print(T t) {
        if (false) System.out.println(t);
        return t;
    }
}
