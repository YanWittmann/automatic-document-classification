package de.yanwittmann.document.model;

import lombok.Data;
import org.json.JSONObject;

@Data
public class DFileCategorization {
    private final String path;
    private final String filename;

    public DFileCategorization retype(String extension) {
        if (extension.contains(".")) {
            extension = extension.substring(extension.lastIndexOf(".") + 1);
        }
        if (filename.contains(".")) {
            return new DFileCategorization(path, filename.substring(0, filename.lastIndexOf(".")) + "." + extension);
        } else {
            return new DFileCategorization(path, filename + "." + extension);
        }
    }

    public DFileCategorization cleanFilename() {
        final String cleanedFilename = filename.replaceAll("[^ a-zA-Z0-9,;:+*#'._€!§$%&()\\[\\]{}-]", "");
        final String path = this.path == null ? "" : this.path.replace("../", "/").replace("//", "/");
        if (cleanedFilename.length() > 255) {
            return new DFileCategorization(path, cleanedFilename.substring(0, 255));
        }
        return new DFileCategorization(path, cleanedFilename);
    }

    @Override
    public String toString() {
        return (path + "/" + filename).replace("../", "/").replace("//", "/");
    }

    public static DFileCategorization fromJson(JSONObject json) {
        return new DFileCategorization(json.optString("path", null), json.optString("filename", null));
    }
}
