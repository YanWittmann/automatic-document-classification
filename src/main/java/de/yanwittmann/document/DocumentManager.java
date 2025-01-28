package de.yanwittmann.document;

import de.yanwittmann.document.ai.ChatUtil;
import de.yanwittmann.document.ai.CompletionClient;
import de.yanwittmann.document.dir.DirectoryScanner;
import de.yanwittmann.document.dir.FileMover;
import de.yanwittmann.document.model.Config;
import de.yanwittmann.document.model.DFileCategorization;
import de.yanwittmann.document.model.TimeStats;
import de.yanwittmann.document.pdf.OCRProcessor;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentManager {

    private final DirectoryScanner scanner;
    private final CompletionClient textCompletion;
    private final CompletionClient imageDetection;
    private final FileMover fileMover;
    private final OCRProcessor ocr = new OCRProcessor();

    public DocumentManager() throws IOException {
        this.scanner = new DirectoryScanner(Config.Props.DOCUMENTS_REF_DIR_BASEPATH.get());
        this.textCompletion = CompletionClient.builder()
                .baseUrl(Config.Props.AI_CHAT_BASEURL.get())
                .model(Config.Props.AI_CHAT_MODEL.get())
                .build();
        this.imageDetection = CompletionClient.builder()
                .baseUrl(Config.Props.AI_CHAT_BASEURL.get())
                .model(Config.Props.AI_IMAGE_MODEL.get())
                .build();
        this.fileMover = new FileMover(Config.Props.DOCUMENTS_MOVE_DIR_BASEPATH.get());
    }

    public static void main(String[] args) throws IOException {
        final DocumentManager documentManager = new DocumentManager();

        final TimeStats totalTime = new TimeStats();

        if (args.length == 0) {
            printErrorBox("No files provided");
            return;
        }

        final List<File> files = new ArrayList<>();
        for (String arg : args) {
            final File file = new File(arg);
            if (file.exists()) {
                if (file.isDirectory()) {
                    files.addAll(FileUtils.listFiles(file, null, true));
                } else {
                    files.add(file);
                }
            } else {
                printErrorBox("File not found: " + arg);
            }
        }

        System.out.printf(String.format("%s - %s - ocr=%s%n%n", "DOCUMENT CLASSIFIER", files.size() + " file" + (files.size() == 1 ? "" : "s"), Config.Props.OCR_METHOD.get()));

        for (int i = 0; i < files.size(); i++) {
            final File processFile = files.get(i);
            final TimeStats fileTime = new TimeStats();

            printHorizontalLine("┌── " + "[%02d / %02d] ".formatted(i + 1, files.size()));
            System.out.printf("│ %s%n", processFile.getName());

            try {
                final DFileCategorization categorization = documentManager.categorizeFile(processFile);
                final DFileCategorization finalCategorization = categorization.cleanFilename().retype(processFile.getName());

                documentManager.fileMover.moveFile(processFile, finalCategorization);
                printStep("Moved file", finalCategorization.toString(), fileTime.stopFormatted());
            } catch (Exception e) {
                printErrorBox("Processing failed: " + e.getMessage());
                e.printStackTrace();
            }

            printHorizontalLine("└");
        }

        printHorizontalLine("┌");
        printStep("Finished classification", files.size() + " file" + (files.size() == 1 ? "" : "s") + " processed", totalTime.stopFormatted());
        printHorizontalLine("└");
    }

    private String getCurrentDate() {
        return LocalDate.now().toString();
    }

    private DFileCategorization categorizeFile(File file) throws Exception {
        final TimeStats ocrTime = new TimeStats();
        final String ocrText;
        if (Config.Props.OCR_METHOD.get().equals("tesseract")) {
            ocrText = ocr.cleanOcrResult(ocr.processFile(file, ocr::runTesseractOCR), 2000);
        } else if (Config.Props.OCR_METHOD.get().equals("ollama")) {
            ocrText = ocr.cleanOcrResult(ocr.processFile(file, f -> imageDetection.generateImageTextCompletion(ChatUtil.fillTemplateFromClasspath("chat/extract-image-content-01.txt", Collections.emptyMap()), f, 0.6)), 2000);
        } else {
            throw new RuntimeException("Unknown OCR method: " + Config.Props.OCR_METHOD.get());
        }
        printStep(Config.Props.OCR_METHOD.get() + " OCR", ocrText.length() + " chars", ocrTime.stopFormatted());

        final JSONArray exampleFiles = new JSONArray();
        for (Map.Entry<String, DirectoryScanner.DirectoryNode> entry : scanner.getExampleFiles(4).entrySet()) {
            exampleFiles.put(new JSONObject()
                    .put("path", entry.getValue().path(true))
                    .put("filename", entry.getKey())
            );
        }

        final Map<String, String> promptParameters = new HashMap<>(Map.of(
                "ocr_text", ocrText,
                "directory_structure", scanner.toShortJson().toString(1),
                "docfiles", scanner.getNodesWithDocfileContent().stream()
                        .map(n -> n.path(true) + " --> " + n.getDocfileContent())
                        .collect(Collectors.joining("\n")),
                "example_filenames", exampleFiles.toString(1),
                "current_date", getCurrentDate(),
                "top_level_directories", new JSONArray(scanner.getTopLevelDirectories().stream()
                        .map(DirectoryScanner.DirectoryNode::getName)
                        .toList()).toString()
        ));

        final String ocrSummary;
        try {
            final TimeStats summaryTime = new TimeStats();
            final String prompt = print(ChatUtil.fillTemplateFromClasspath("chat/summarize-file-01.txt", promptParameters));
            final String completion = print(ChatUtil.filterThinking(textCompletion.generateTextCompletion(prompt)));
            ocrSummary = completion;
            printStep("Document summarized", ocrSummary.length() + " chars", summaryTime.stopFormatted());
            promptParameters.put("ocr_summary", ocrSummary);
        } catch (Exception e) {
            throw new Exception("Summarization failed: " + e.getMessage(), e);
        }

        final String filename;
        final TimeStats nameTime = new TimeStats();
        filename = retry(2, "Filename generation failed", () -> {
            final String prompt = print(ChatUtil.fillTemplateFromClasspath("chat/generate-filename-01.txt", promptParameters));
            final String completion = print(textCompletion.generateTextCompletion(prompt));
            final JSONObject completionJson = print(ChatUtil.extractJsonObject(completion));

            if (completionJson == null) {
                throw new RuntimeException("No valid JSON response for filename generation.");
            }

            final String extracted = DFileCategorization.fromJson(completionJson).getFilename();
            if (extracted == null) {
                throw new RuntimeException("No filename found in response JSON.");
            }
            return extracted;
        });
        printStep("Filename generated", filename, nameTime.stopFormatted());
        promptParameters.put("suggested_filename", filename);

        final String path;
        final TimeStats pathTime = new TimeStats();
        path = retry(2, "Path generation failed", () -> {
            final String prompt = print(ChatUtil.fillTemplateFromClasspath("chat/suggest-path-01.txt", promptParameters));
            final String completion = print(textCompletion.generateTextCompletion(prompt));
            final JSONObject completionJson = print(ChatUtil.extractJsonObject(completion));

            if (completionJson == null) {
                throw new RuntimeException("No valid JSON response for path generation.");
            }

            final String extracted = DFileCategorization.fromJson(completionJson).getPath();
            if (extracted == null) {
                throw new RuntimeException("No path found in response JSON.");
            }
            return extracted;
        });
        printStep("Path generated", path, pathTime.stopFormatted());

        return new DFileCategorization(path, filename);
    }

    private static <T> T print(T t) {
        if (false) System.out.println(t);
        return t;
    }

    private static final int TIME_WIDTH = 7;
    private static final int ACTION_WIDTH = 20;
    private static final int DETAILS_WIDTH = 30;
    private static final int DIVIDER_LINE_LENGTH = 80;

    private static void printStep(String action, String details, String time) {
        final String timePart = !time.isEmpty() ? String.format("[%" + TIME_WIDTH + "s]", time) : " ".repeat(TIME_WIDTH + 2);
        final String actionPart = String.format("%-" + ACTION_WIDTH + "s", action + ":");
        final String detailsPart = String.format("%-" + DETAILS_WIDTH + "s", details);

        System.out.printf("│ %s %s %s%n", timePart, actionPart, detailsPart);
    }

    private static void printErrorBox(String message) {
        printHorizontalLine("├");
        System.out.printf("│ ERROR: %s%n", message);
        printHorizontalLine("└");
    }

    private static void printHorizontalLine(String prefix) {
        System.out.println(prefix + "─".repeat(DIVIDER_LINE_LENGTH - prefix.length() - 1));
    }

    private static <T> T retry(int times, String failWrapperMessage, ThrowingSupplier<T> supplier) {
        Exception lastException = null;
        for (int i = 0; i < times; i++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                System.out.println("│ Attempt " + (i + 1) + " of " + times + " failed: " + e.getMessage());
                lastException = e;
            }
        }
        if (lastException == null) {
            throw new RuntimeException(failWrapperMessage);
        }
        throw new RuntimeException(failWrapperMessage + ": " + lastException.getMessage(), lastException);
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}