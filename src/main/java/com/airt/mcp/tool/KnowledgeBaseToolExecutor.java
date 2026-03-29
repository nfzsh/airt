package com.airt.mcp.tool;

import com.airt.config.AirtProperties;
import com.airt.mcp.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 知识库搜索工具执行器
 *
 * 基于文件系统的关键词匹配搜索，支持 .md / .txt / .yaml / .json 文件
 */
@Slf4j
public class KnowledgeBaseToolExecutor implements ToolExecutor {

    private final AirtProperties.ToolConfig config;

    public KnowledgeBaseToolExecutor(AirtProperties.ToolConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "search_knowledge_base";
    }

    @Override
    public String getDescription() {
        return "Search the internal knowledge base for documents, policies, architecture docs, and other reference materials.";
    }

    @Override
    public String execute(Map<String, Object> parameters) {
        String query = getStringParam(parameters, "query");
        if (query == null || query.isBlank()) {
            return "Error: missing required parameter 'query'";
        }

        List<String> searchPaths = config != null && config.getPaths() != null && !config.getPaths().isEmpty()
                ? config.getPaths()
                : List.of("./knowledge/");

        List<SearchResult> results = new ArrayList<>();
        String[] keywords = query.toLowerCase().split("\\s+");

        for (String pathStr : searchPaths) {
            Path basePath = Paths.get(pathStr);
            if (!Files.exists(basePath)) {
                log.debug("Knowledge base path does not exist: {}", basePath);
                continue;
            }

            try (Stream<Path> files = Files.walk(basePath, 5)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isReadableFile)
                        .forEach(file -> searchInFile(file, keywords, results));
            } catch (IOException e) {
                log.error("Error scanning knowledge base path {}: {}", pathStr, e.getMessage());
            }
        }

        if (results.isEmpty()) {
            return String.format("No documents found matching query: \"%s\" in knowledge base paths: %s",
                    query, searchPaths);
        }

        // 按匹配度排序，取 top 5
        results.sort(Comparator.comparingInt(SearchResult::score).reversed());
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d relevant documents for: \"%s\"\n\n", results.size(), query));
        int limit = Math.min(results.size(), 5);
        for (int i = 0; i < limit; i++) {
            SearchResult r = results.get(i);
            sb.append(String.format("[%d] %s (score: %d)\n", i + 1, r.filePath, r.score));
            sb.append(r.snippet).append("\n\n");
        }
        return sb.toString();
    }

    private void searchInFile(Path file, String[] keywords, List<SearchResult> results) {
        try {
            String content = Files.readString(file);
            String lowerContent = content.toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                int idx = 0;
                int count = 0;
                while ((idx = lowerContent.indexOf(kw, idx)) != -1) {
                    count++;
                    idx += kw.length();
                }
                score += count;
            }
            if (score > 0) {
                String snippet = extractSnippet(content, keywords[0], 300);
                results.add(new SearchResult(file.toString(), score, snippet));
            }
        } catch (IOException e) {
            log.debug("Cannot read file: {}", file);
        }
    }

    private String extractSnippet(String content, String keyword, int maxLen) {
        String lower = content.toLowerCase();
        int idx = lower.indexOf(keyword);
        int start = Math.max(0, idx - 100);
        int end = Math.min(content.length(), start + maxLen);
        String snippet = content.substring(start, end).replace("\n", " ").trim();
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        return snippet;
    }

    private boolean isReadableFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".yaml")
                || name.endsWith(".yml") || name.endsWith(".json") || name.endsWith(".csv");
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    private record SearchResult(String filePath, int score, String snippet) {}
}
