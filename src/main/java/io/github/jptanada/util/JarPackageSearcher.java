package io.github.jptanada.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;

public class JarPackageSearcher {

	public static void main(String[] args) throws URISyntaxException {
        String jarDirectoriesFile = "jar_directories.txt";
        String targetPackagesFile = "javax_packages.txt";

        List<String> matchedJars = new ArrayList<>(); // To store JARs with matched imports

        try {
            // Read JAR file paths and target packages from files
            List<String> jarFilePaths = readLinesFromFile(jarDirectoriesFile);
            List<String> targetPackages = readLinesFromFile(targetPackagesFile);

            // Process each JAR file
            for (String jarFilePath : jarFilePaths) {
                System.out.println("Inspecting JAR: " + jarFilePath);
                boolean hasMatch = inspectJarFile(jarFilePath, targetPackages);

                if (hasMatch && !matchedJars.contains(jarFilePath)) {
                    matchedJars.add(jarFilePath); // Add JAR to the matched list if not already present
                }
            }

            // Print the list of JARs with matched imports
            System.out.println("\nJAR files containing classes that imported the target packages:");
            for (String matchedJar : matchedJars) {
                System.out.println(matchedJar);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean inspectJarFile(String jarFilePath, List<String> targetPackages) {
        boolean hasMatch = false;

        try {
            // Open the JAR file
            JarFile jarFile = new JarFile(jarFilePath);
            var entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Only process .class files
                if (entryName.endsWith(".class")) {
                    InputStream is = jarFile.getInputStream(entry);
                    byte[] classBytes = is.readAllBytes();

                    // Decompile the class bytes into readable Java source
                    String classSource = decompileClass(classBytes);

                    // Check if the source contains any of the target packages
                    for (String targetPackage : targetPackages) {
                        if (classSource.contains("import " + targetPackage)) {
                            System.out.println("Class " + entryName + " imports package: " + targetPackage);
                            hasMatch = true;
                        }
                    }
                }
            }

            jarFile.close();
        } catch (IOException e) {
            System.err.println("Error processing JAR file: " + jarFilePath);
            e.printStackTrace();
        }

        return hasMatch;
    }

    private static String decompileClass(byte[] classBytes) {
        try {
            // Write the bytecode to a temporary file
            File tempFile = File.createTempFile("tempClass", ".class");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(classBytes);
            }

            // Prepare to capture the decompiled output
            StringBuilder decompiledOutput = new StringBuilder();
            OutputSinkFactory outputSinkFactory = new OutputSinkFactory() {
                @Override
                public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                    return Collections.singletonList(SinkClass.STRING);
                }

                @Override
                public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                    return (Sink<T>) t -> decompiledOutput.append(t).append("\n");
                }
            };

            // Use CFR driver to decompile the file
            CfrDriver driver = new CfrDriver.Builder()
                    .withOutputSink(outputSinkFactory)
                    .build();

            driver.analyse(Collections.singletonList(tempFile.getAbsolutePath()));

            // Clean up the temporary file
            tempFile.delete();

            // Return the decompiled source code
            return decompiledOutput.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static List<String> readLinesFromFile(String filePath) throws IOException, URISyntaxException {
        // Accessing the file in the same package or directory as the class
        URL resourceUrl = JarPackageSearcher.class.getResource(filePath);

        if (resourceUrl == null) {
            System.out.println("File not found!");
            return new ArrayList<>();
        }

        Path packagesFilePath = Paths.get(resourceUrl.toURI());

        // Read javax package names
        return Files.readAllLines(packagesFilePath);
    }

}
