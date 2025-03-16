package com.agenarisk.api.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Eugene Dementiev
 */
public class CsvReader {
	public static List<List<String>> readCsv(Path filePath) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = br.readLine()) != null) {
                List<String> row = Arrays.asList(line.split(","));
                data.add(row);
            }
        }

        return data;
    }
	
	public static List<String> readHeaders(Path filePath) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
			String line = br.readLine();
			if (line == null){
				return new ArrayList<>();
			}
            return Arrays.asList(line.split(","));
        }
    }
}
