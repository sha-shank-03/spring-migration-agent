package dev.springmig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Json {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
        .setStrictness(Strictness.STRICT).serializeNulls().create();
    static void write(Path path, Object data) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(data) + "\n");
    }
}
