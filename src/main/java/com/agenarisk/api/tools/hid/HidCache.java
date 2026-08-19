package com.agenarisk.api.tools.hid;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONException;
import org.json.JSONObject;

public class HidCache {

    public JSONObject importHIDCache(File cacheFile) throws JSONException, IOException {
        return new JSONObject(new String(Files.readAllBytes(Paths.get(cacheFile.getCanonicalPath()))));
    }

    public void exportHIDCache(File cacheFile, JSONObject cachedTree) throws IOException, JSONException {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            cachedTree.write(writer);
        }
    }
}
