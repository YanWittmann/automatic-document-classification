package de.yanwittmann.document.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Properties;

public abstract class Config {

    private final static Properties properties = new Properties();

    static {
        try {
            properties.load(Config.class.getResource("/config.properties").openStream());
        } catch (Exception e) {
            System.err.println("Error loading config.properties file");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String get(Props prop, String defaultValue) {
        return get(prop.key, defaultValue);
    }

    @Getter
    @AllArgsConstructor
    public enum Props {
        AI_CHAT_MODEL("ai.chat.model", "llama3:8b"),
        AI_IMAGE_MODEL("ai.image.model", "llama3.2-vision"),
        AI_CHAT_BASEURL("ai.chat.baseurl", "http://localhost:11434"),
        DOCUMENTS_REF_DIR_BASEPATH("documents.refdir.basepath", null),
        DOCUMENTS_MOVE_DIR_BASEPATH("documents.movedir.basepath", null),
        OCR_LANGUAGE("ocr.language", "eng"),
        OCR_METHOD("ocr.method", "ollama"),
        ;

        private final String key;
        private final String defaultValue;

        public String get() {
            return Config.get(key, defaultValue);
        }
    }
}
