package com.analysis.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.analysis.model.dto.DatasetInfo;
import com.analysis.model.entity.ColumnMetadata;
import com.analysis.model.entity.Dataset;
import com.analysis.repository.DuckDBRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DuckDBRepository duckDBRepository;
    private final MetadataService metadataService;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @PostConstruct
    public void init() throws SQLException {
        duckDBRepository.initSchema();
        Path path = Paths.get(uploadPath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                log.error("Failed to create upload directory", e);
            }
        }
    }

    public Dataset uploadDataset(MultipartFile file, String datasetName) throws Exception {
        return uploadDataset(file, datasetName, null);
    }

    public Dataset uploadDataset(MultipartFile file, String datasetName, Long groupId) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (groupId != null && duckDBRepository.findDatasetGroupById(groupId) == null) {
            throw new IllegalArgumentException("工作区不存在: " + groupId);
        }

        // 避免 Excel 解析 OOM，限制最大文件体积 (例如 50MB) 
        long MAX_FILE_SIZE = 50 * 1024 * 1024;
        if ((originalFilename.toLowerCase().endsWith(".xlsx") || originalFilename.toLowerCase().endsWith(".xls")) 
            && file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Excel文件过大，为了防止系统内存溢出，目前限制最大上传 50MB。请转存为 CSV 后重试。");
        }

        String tableName = generateTableName(datasetName);
        Path filePath = Paths.get(uploadPath, UUID.randomUUID() + "_" + originalFilename);
        Files.copy(file.getInputStream(), filePath);

        try {
            if (originalFilename.toLowerCase().endsWith(".csv")) {
                importCsv(filePath.toString(), tableName);
            } else if (originalFilename.toLowerCase().endsWith(".xlsx")
                    || originalFilename.toLowerCase().endsWith(".xls")) {
                importExcel(filePath.toString(), tableName);
            } else {
                throw new IllegalArgumentException("不支持的文件格式，仅支持CSV和Excel");
            }

            long rowCount = duckDBRepository.getTableRowCount(tableName);
            List<ColumnMetadata> columns = metadataService.extractColumnMetadata(tableName, null);

            Dataset dataset = new Dataset();
            dataset.setGroupId(groupId);
            dataset.setName(datasetName);
            dataset.setTableName(tableName);
            dataset.setOriginalFileName(originalFilename);
            dataset.setRowCount(rowCount);
            dataset.setColumnCount(columns.size());

            Long datasetId = duckDBRepository.saveDataset(dataset);
            dataset.setId(datasetId);

            for (ColumnMetadata col : columns) {
                col.setDatasetId(datasetId);
                duckDBRepository.saveColumnMetadata(col);
            }

            // 🌟 触发 RAG 注入: 将刚才提取到的表结构存入 Milvus 向量库
            metadataService.saveMetadataToVectorStore(tableName, datasetId, columns);

            if (groupId != null) {
                // 阶段一预留：此处可接入自动关系识别服务（当前先保留 Hook）
                log.info("[Dataset Upload] dataset {} has been attached to group {}", datasetId, groupId);
            }

            return dataset;
        } finally {
            Files.deleteIfExists(filePath);
        }
    }

    private void importCsv(String filePath, String tableName) throws SQLException {
        String sql = String.format("CREATE TABLE %s AS SELECT * FROM read_csv_auto('%s', header=true)",
                tableName, filePath.replace("\\", "/"));
        duckDBRepository.executeUpdate(sql);
    }

    private void importExcel(String filePath, String tableName) throws Exception {
        List<String> headers = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
                Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int idx = 0; idx < headerRow.getLastCellNum(); idx++) {
                    Cell cell = headerRow.getCell(idx);
                    String rawName = (cell != null) ? getCellValue(cell).toString().trim() : "";
                    String colName = sanitizeColumnName(rawName);
                    // 如果列名为空（比如空白表头），自动生成 fallback 名称
                    if (colName.isEmpty()) {
                        colName = "col_" + (idx + 1);
                    }
                    headers.add(colName);
                }
            }

            // 处理重复列名：加后缀 _2, _3 ...
            List<String> uniqueHeaders = new ArrayList<>();
            for (String h : headers) {
                String candidate = h;
                int suffix = 2;
                while (uniqueHeaders.contains(candidate)) {
                    candidate = h + "_" + suffix++;
                }
                uniqueHeaders.add(candidate);
            }
            headers = uniqueHeaders;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    List<Object> rowData = new ArrayList<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        rowData.add(cell != null ? getCellValue(cell) : null);
                    }
                    rows.add(rowData);
                }
            }
        }

        // 使用双引号包裹列名，防止中文列名或关键字冲突
        StringBuilder createSql = new StringBuilder();
        createSql.append("CREATE TABLE ").append(tableName).append(" (");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0)
                createSql.append(", ");
            createSql.append('"').append(headers.get(i)).append('"').append(" VARCHAR");
        }
        createSql.append(")");
        log.info("[Excel Import] CREATE SQL: {}", createSql);
        duckDBRepository.executeUpdate(createSql.toString());

        // 构建参数化查询模板
        StringBuilder insertSql = new StringBuilder();
        insertSql.append("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) insertSql.append(", ");
            insertSql.append('"').append(headers.get(i)).append('"');
        }
        insertSql.append(") VALUES (");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) insertSql.append(", ");
            insertSql.append("?");
        }
        insertSql.append(")");

        // 按照 1000 行一批进行分批插入
        int batchSize = 1000;
        List<List<Object>> batchArgs = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            batchArgs.add(rows.get(i));
            if (batchArgs.size() == batchSize || i == rows.size() - 1) {
                duckDBRepository.executeBatch(insertSql.toString(), batchArgs);
                batchArgs.clear();
            }
        }
    }

    private Object getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    public List<Dataset> getAllDatasets() throws SQLException {
        return duckDBRepository.findAllDatasets();
    }

    public List<Dataset> getAllDatasets(Long groupId) throws SQLException {
        return duckDBRepository.findAllDatasets(groupId);
    }

    public Dataset getDatasetById(Long id) throws SQLException {
        return duckDBRepository.findDatasetById(id);
    }

    public DatasetInfo getDatasetInfo(Long id) throws SQLException {
        Dataset dataset = duckDBRepository.findDatasetById(id);
        if (dataset == null) {
            return null;
        }

        List<ColumnMetadata> columns = duckDBRepository.findColumnsByDatasetId(id);

        DatasetInfo info = new DatasetInfo();
        info.setId(dataset.getId());
        info.setName(dataset.getName());
        info.setTableName(dataset.getTableName());
        info.setRowCount(dataset.getRowCount());
        info.setColumnCount(dataset.getColumnCount());

        List<DatasetInfo.ColumnInfo> columnInfos = new ArrayList<>();
        for (ColumnMetadata col : columns) {
            DatasetInfo.ColumnInfo columnInfo = new DatasetInfo.ColumnInfo();
            columnInfo.setName(col.getColumnName());
            columnInfo.setType(col.getDataType());
            columnInfo.setDistinctCount(col.getDistinctCount());
            columnInfo.setMinValue(col.getMinValue());
            columnInfo.setMaxValue(col.getMaxValue());
            columnInfos.add(columnInfo);
        }
        info.setColumns(columnInfos);

        return info;
    }

    public void deleteDataset(Long id) throws SQLException {
        duckDBRepository.deleteDataset(id);
    }

    public Dataset updateDatasetDescription(Long datasetId, String descriptionMd) throws SQLException {
        Dataset existing = duckDBRepository.findDatasetById(datasetId);
        if (existing == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        duckDBRepository.updateDatasetDescription(datasetId, descriptionMd);
        return duckDBRepository.findDatasetById(datasetId);
    }

    private String generateTableName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return "data_" + sanitized + "_" + System.currentTimeMillis();
    }

    private String sanitizeColumnName(String name) {
        if (name == null) return "";
        // 保留中文（Unicode字母）、英文字母、数字和下划线
        String sanitized = name.replaceAll("[^\\p{L}\\p{N}_]", "_").trim();
        // 去除首尾多余下划线
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        // 连续下划线合并
        sanitized = sanitized.replaceAll("_+", "_");
        return sanitized;
    }
}
