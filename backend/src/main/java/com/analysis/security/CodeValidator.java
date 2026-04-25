package com.analysis.security;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CodeValidator {

    private static final Set<String> FORBIDDEN_IMPORTS = Set.of(
            "os", "subprocess", "sys", "shutil", "socket", "pickle",
            "multiprocessing", "threading", "ctypes", "importlib");

    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
            "eval", "exec", "compile", "open", "__import__", "getattr", "setattr",
            "delattr", "globals", "locals", "vars", "dir", "type", "isinstance");

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:import|from)\\s+([\\w.]+)",
            Pattern.MULTILINE);

    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "\\b(eval|exec|compile|open|__import__|getattr|setattr|delattr)\\s*\\(");

    public boolean validate(String pythonCode) {
        if (pythonCode == null || pythonCode.trim().isEmpty()) {
            return false;
        }

        var importMatcher = IMPORT_PATTERN.matcher(pythonCode);
        while (importMatcher.find()) {
            String module = importMatcher.group(1).split("\\.")[0];
            if (FORBIDDEN_IMPORTS.contains(module)) {
                log.warn("Forbidden import detected: {}", module);
                return false;
            }
        }

        if (FUNCTION_PATTERN.matcher(pythonCode).find()) {
            log.warn("Forbidden function call detected");
            return false;
        }

        if (pythonCode.contains("__") && !pythonCode.contains("__name__")) {
            log.warn("Dunder methods access detected");
            return false;
        }

        return true;
    }

    public Set<String> getAllowedImports() {
        return Set.of(
                "pandas", "numpy", "scipy", "sklearn", "matplotlib", "seaborn",
                "json", "datetime", "math", "statistics", "collections", "itertools");
    }
}
