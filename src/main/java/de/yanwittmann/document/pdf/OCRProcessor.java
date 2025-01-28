package de.yanwittmann.document.pdf;

import de.yanwittmann.document.model.Config;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class OCRProcessor {
    public String processFile(File inputFile) throws IOException {
        if (isPDF(inputFile)) {
            return processPDF(inputFile);
        } else if (isImage(inputFile)) {
            return runTesseractOCR(inputFile);
        } else {
            return FileUtils.readFileToString(inputFile, "UTF-8");
        }
    }

    private boolean isPDF(File file) {
        return file.getName().toLowerCase().endsWith(".pdf");
    }

    private boolean isImage(File file) {
        final String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private String processPDF(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.getNumberOfPages() > 0) {
                return extractTextFromPDF(document);
            }
            return "";
        }
    }

    private String extractTextFromPDF(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        Path tempDir = Files.createTempDirectory("pdf_images");
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, 300);
            File tempImage = new File(tempDir.toFile(), "page_" + i + ".png");
            ImageIO.write(image, "png", tempImage);
            text.append(runTesseractOCR(tempImage));
        }

        FileUtils.deleteDirectory(tempDir.toFile());
        return text.toString();
    }

    private String runTesseractOCR(File imageFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", imageFile.getParent() + ":/data",
                "tesseractshadow/tesseract4re",
                "tesseract", "/data/" + imageFile.getName(), "stdout",
                "-l", Config.Props.OCR_LANGUAGE.get()
        );

        final Process process = pb.start();
        return new String(process.getInputStream().readAllBytes());
    }

    public String cleanOcrResult(String content, int maxLength) {
        content = content
                .replaceAll(" {2,}", " ")
                .replaceAll("\n{2,}", "\n")
                .replaceAll("[\n ]{2,}", "\n");

        if (content.length() <= maxLength) {
            return content;
        }

        final int half = maxLength / 2;
        return content.substring(0, half) + "..." + content.substring(content.length() - half);
    }
}
