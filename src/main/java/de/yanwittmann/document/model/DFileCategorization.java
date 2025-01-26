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

    @Override
    public String toString() {
        return path + "/" + filename;
    }

    public static DFileCategorization fromJson(JSONObject json) {
        return new DFileCategorization(json.getString("path"), json.getString("filename"));
    }
}
