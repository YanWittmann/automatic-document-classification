package de.yanwittmann.document.dir;

import de.yanwittmann.document.model.DFileCategorization;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class FileMover {
    private final String rootPath;

    public FileMover(String rootPath) {
        this.rootPath = rootPath;
    }

    public void moveFile(File sourceFile, DFileCategorization location) throws IOException {
        File targetDir = Paths.get(rootPath, location.getPath()).toFile();
        FileUtils.forceMkdir(targetDir);

        File targetFile = new File(targetDir, location.getFilename());
        if (targetFile.exists()) {
            targetFile = handleDuplicate(targetFile);
        }

        FileUtils.moveFile(sourceFile, targetFile);
    }

    private File handleDuplicate(File originalFile) {
        String baseName = originalFile.getName().replaceFirst("[.][^.]+$", "");
        String extension = originalFile.getName().substring(baseName.length());

        int counter = 1;
        File newFile;
        do {
            newFile = new File(originalFile.getParent(), baseName + "_" + counter + extension);
            counter++;
        } while (newFile.exists());

        return newFile;
    }
}
