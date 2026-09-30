package com.opslens.application.his;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeContentExtractor {

    private static final int MAX_SHEETS = 4;
    private static final int MAX_ROWS_PER_SHEET = 160;
    private static final int MAX_CELLS_PER_ROW = 16;
    private static final int MAX_SNIPPETS = 3;
    private static final int MAX_SNIPPET_LENGTH = 420;

    public List<ExtractedSnippet> extract(Path path, Set<String> keywords) {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return extractExcel(path, keywords);
            }
            if (fileName.endsWith(".pdf")) {
                return extractPdf(path, keywords);
            }
        } catch (IOException | RuntimeException ex) {
            return List.of(new ExtractedSnippet("문서 추출 실패", "문서 내용을 읽는 중 오류가 발생했습니다. 파일명 기반 매칭 결과만 사용합니다."));
        }
        return List.of();
    }

    private List<ExtractedSnippet> extractExcel(Path path, Set<String> keywords) throws IOException {
        List<ExtractedSnippet> snippets = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        try (Workbook workbook = WorkbookFactory.create(path.toFile())) {
            int sheetLimit = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
            for (int sheetIndex = 0; sheetIndex < sheetLimit && snippets.size() < MAX_SNIPPETS; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET) && snippets.size() < MAX_SNIPPETS; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }
                    String rowText = rowText(row, formatter);
                    if (rowText.isBlank()) {
                        continue;
                    }
                    if (matches(rowText, keywords) || looksLikeColumnDefinition(rowText)) {
                        snippets.add(new ExtractedSnippet(sheet.getSheetName() + " " + (rowIndex + 1) + "행", abbreviate(rowText)));
                    }
                }
            }
        }
        return snippets;
    }

    private List<ExtractedSnippet> extractPdf(Path path, Set<String> keywords) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), 5));
            String text = stripper.getText(document);
            List<ExtractedSnippet> snippets = new ArrayList<>();
            String[] lines = text.split("\\R");
            for (int index = 0; index < lines.length && snippets.size() < MAX_SNIPPETS; index++) {
                String line = normalize(lines[index]);
                if (line.isBlank()) {
                    continue;
                }
                if (matches(line, keywords) || line.contains("배포") || line.contains("서비스") || line.contains("시작") || line.contains("중지")) {
                    snippets.add(new ExtractedSnippet("PDF " + (index + 1) + "번째 문장", abbreviate(line)));
                }
            }
            if (snippets.isEmpty() && !text.isBlank()) {
                snippets.add(new ExtractedSnippet("PDF 본문 일부", abbreviate(normalize(text))));
            }
            return snippets;
        }
    }

    private String rowText(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        short lastCellNum = row.getLastCellNum();
        int cellLimit = Math.min(lastCellNum < 0 ? 0 : lastCellNum, MAX_CELLS_PER_ROW);
        for (int cellIndex = 0; cellIndex < cellLimit; cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            if (cell == null) {
                continue;
            }
            String value = normalize(formatter.formatCellValue(cell));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return String.join(" | ", values);
    }

    private boolean matches(String text, Set<String> keywords) {
        String upperText = text.toUpperCase(Locale.ROOT);
        return keywords.stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .map(keyword -> keyword.toUpperCase(Locale.ROOT))
            .anyMatch(upperText::contains);
    }

    private boolean looksLikeColumnDefinition(String rowText) {
        String upper = rowText.toUpperCase(Locale.ROOT);
        return upper.contains("COLUMN")
            || upper.contains("DATA")
            || upper.contains("NULL")
            || upper.contains("VARCHAR")
            || upper.contains("NUMBER")
            || rowText.contains("컬럼")
            || rowText.contains("항목")
            || rowText.contains("필수")
            || rowText.contains("길이");
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value) {
        String normalized = normalize(value);
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }

    public Set<String> snippetKeywords(List<String> seeds) {
        Set<String> keywords = new LinkedHashSet<>();
        seeds.stream()
            .filter(seed -> seed != null && !seed.isBlank())
            .map(seed -> seed.replace("_FAST", ""))
            .forEach(keywords::add);
        return keywords;
    }

    public record ExtractedSnippet(String title, String content) {
    }
}
