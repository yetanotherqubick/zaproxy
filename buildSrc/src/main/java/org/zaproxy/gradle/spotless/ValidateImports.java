/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2023 The ZAP Development Team
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
package org.zaproxy.gradle.spotless;

import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A Spotless formatter step that rejects disallowed imports. */
public final class ValidateImports {

    private static final String NAME = "validateImports";

    private ValidateImports() {}

    /**
     * Creates a new {@link FormatterStep}.
     *
     * @param invalidImports imports that are not allowed, with their corresponding error messages
     * @return a {@link FormatterStep}
     */
    public static FormatterStep create(Map<String, String> invalidImports) {
        return FormatterStep.create(NAME, new State(invalidImports), State::toFormatter);
    }

    private static final class State implements Serializable {

        private static final long serialVersionUID = 1L;

        private final LinkedHashMap<String, String> invalidImports;

        State(Map<String, String> invalidImports) {
            this.invalidImports = new LinkedHashMap<>(Objects.requireNonNull(invalidImports));
        }

        FormatterFunc toFormatter() {
            return input -> {
                for (var entry : invalidImports.entrySet()) {
                    if (input.contains(entry.getKey())) {
                        throw new InvalidImportException(entry.getValue());
                    }
                }
                return input;
            };
        }
    }

    private static final class InvalidImportException extends AssertionError {

        private static final long serialVersionUID = 1L;

        InvalidImportException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
