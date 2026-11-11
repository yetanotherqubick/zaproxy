/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2019 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.zaproxy.zap.tasks.internal.MainAddOn;
import org.zaproxy.zap.tasks.internal.MainAddOnsData;
import org.zaproxy.zap.tasks.internal.Utils;

@DisableCachingByDefault(because = "Downloads add-ons from remote URLs.")
public abstract class DownloadMainAddOns extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getAddOnsData();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void download() throws IOException {
        MainAddOnsData data = parseData();
        Files.createDirectories(getOutputDir().get().getAsFile().toPath());
        checkExistingFiles(data);

        for (MainAddOn addOn : data.getAddOns()) {
            downloadFile(addOn);
        }
    }

    private void checkExistingFiles(MainAddOnsData data) throws IOException {
        File[] files = getOutputDir().get().getAsFile().listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        List<Path> existingFiles = Stream.of(files).map(File::toPath).collect(Collectors.toList());
        for (Iterator<MainAddOn> it = data.getAddOns().iterator(); it.hasNext(); ) {
            MainAddOn addOn = it.next();
            Path file = addOn.getOutputFile();
            if (existingFiles.contains(file) && hasSameHash(file, addOn)) {
                existingFiles.remove(file);
                it.remove();
            }
        }

        for (Path file : existingFiles) {
            Files.deleteIfExists(file);
        }
    }

    private void downloadFile(MainAddOn addOn) throws IOException {
        Path file = addOn.getOutputFile();
        Path temporaryFile = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
        try {
            URLConnection connection = URI.create(addOn.getUrl()).toURL().openConnection();
            connection.setConnectTimeout(60_000);
            connection.setReadTimeout(60_000);

            try (InputStream input = connection.getInputStream()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String computedHash = Utils.hash(temporaryFile, addOn);
            if (!computedHash.equalsIgnoreCase(addOn.getHash())) {
                throw new IOException(
                        "Hash mismatch for file "
                                + file
                                + " expected "
                                + addOn.getHash()
                                + " but got "
                                + computedHash);
            }

            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static boolean hasSameHash(Path file, MainAddOn addOn) {
        try {
            return Utils.hash(file, addOn).equalsIgnoreCase(addOn.getHash());
        } catch (Exception ignore) {
            // Ignore
        }
        return false;
    }

    private MainAddOnsData parseData() throws IOException {
        MainAddOnsData data = Utils.parseData(getAddOnsData().get().getAsFile().toPath());
        Path outputDirectory = getOutputDir().get().getAsFile().toPath();
        data.getAddOns()
                .forEach(
                        addOn -> {
                            String fileName = getFileName(addOn.getUrl());
                            addOn.setOutputFile(outputDirectory.resolve(fileName));
                        });
        return data;
    }

    private static String getFileName(String url) {
        int index = url.lastIndexOf("/");
        if (index == -1) {
            throw new InvalidUserDataException("The url does not contain a file name: " + url);
        }
        return url.substring(index + 1);
    }
}
