package com.agenarisk.api.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *
 * @author Eugene Dementiev
 */
public class CsvWriter {
    /**
     * Writes the provided data to the specified path as a CSV file.
	 * @param <T> Type of value in cells
     * @param data The data to write, represented as a List of List of Strings.
     * @param path The path to write the CSV file to.
     * @throws IOException If an I/O error occurs.
     */
    public static <T extends Object> void writeCsv(List<List<T>> data, Path path) throws IOException {
        writeCsv(data, path, ",");
    }

	/**
     * Writes the provided data to the specified path as a CSV file.
	 * @param <T> Type of value in cells
     * @param data The data to write, represented as a List of List of Strings.
     * @param path The path to write the CSV file to.
     * @param separator The separator to use in the CSV file, defaults to ',' if null.
     * @throws IOException If an I/O error occurs.
     */
    public static <T extends Object> void writeCsv(List<List<T>> data, Path path, String separator) throws IOException {
		String actualSeparator = Optional.ofNullable(separator).orElseGet(() -> ",");
        List<String> lines = data.stream()
                .map(row -> row.stream()
						.map(String::valueOf)
                        .map(CsvWriter::escapeCsvField)
                        .collect(Collectors.joining(actualSeparator)))
                .collect(Collectors.toList());
        Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Escapes CSV fields to handle cases where they contain special characters.
     * @param field The CSV field value.
     * @return The properly escaped field.
     */
    private static String escapeCsvField(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            field = field.replace("\"", "\"\"");
            return "\"" + field + "\"";
        }
        return field;
    }
}
