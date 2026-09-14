package com.xzkj.health.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 动态 SQL 的硬性安全边界。
 */
public final class AiSqlGuard {

    private static final int MAX_SQL_LENGTH = 5000;
    private static final int MAX_RESULT_ROWS = 100;
    private static final int MAX_RECENT_TOP_QUERY_ROWS = 10;

    private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN)\\s+([A-Za-z0-9_\\.\\[\\]]+)"
    );
    private static final Pattern CTE_NAME_PATTERN = Pattern.compile(
            "(?i)\\b([A-Za-z_][A-Za-z0-9_]*)\\b\\s+AS\\s*\\("
    );
    private static final Pattern LEADING_TOP_PATTERN = Pattern.compile(
            "(?i)^\\s*(DISTINCT\\s+)?TOP\\s*\\(?\\s*(\\d+)\\s*\\)?\\s+"
    );
    private static final Pattern LEADING_DISTINCT_PATTERN = Pattern.compile("(?i)^\\s*DISTINCT\\b");
    private static final Pattern STANDALONE_WILDCARD_PATTERN = Pattern.compile(
            "(?i)(^|,)\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)?\\*\\s*(?=,|$)"
    );
    private static final Pattern ORDER_BY_TIME_PATTERN = Pattern.compile(
            "(?i)\\bORDER\\s+BY\\b[\\s\\S]*\\b(record_time|create_time)\\b"
    );
    private static final Pattern SMALL_SCOPE_FILTER_PATTERN = Pattern.compile(
            "(?i)\\b(user_code|emp_code|emp_name|dept_name)\\b\\s*="
    );

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "V_HEALTH_RECORD",
            "V_WARNING_RECORD",
            "EMPLOYEE",
            "DEPARTMENT",
            "JOB_TYPE"
    );

    private static final List<String> DANGEROUS_KEYWORDS = List.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE",
            "ALTER", "CREATE", "EXEC", "EXECUTE", "MERGE",
            "GRANT", "REVOKE", "WAITFOR", "SHUTDOWN", "BACKUP",
            "RESTORE", "DBCC", "USE", "OPENROWSET", "OPENDATASOURCE",
            "OPENQUERY", "INTO"
    );

    private static final List<Pattern> TIME_FILTER_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(record_time|create_time)\\b\\s*(>=|>|=|<|<=|BETWEEN)"),
            Pattern.compile("(?i)CAST\\s*\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)?(record_time|create_time)\\s+AS\\s+DATE\\s*\\)\\s*=")
    );

    private AiSqlGuard() {}

    public static String sanitizeAndValidate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("安全校验失败：SQL 不能为空");
        }

        String normalized = stripTrailingSemicolons(sql.trim());
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("安全校验失败：SQL 不能为空");
        }
        if (normalized.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("安全校验失败：SQL 过长");
        }

        String upperSql = normalized.toUpperCase(Locale.ROOT);
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("WITH")) {
            throw new IllegalArgumentException("安全校验失败：只允许 SELECT 查询");
        }
        if (containsComment(normalized)) {
            throw new IllegalArgumentException("安全校验失败：SQL 不允许包含注释");
        }
        if (containsMultipleStatements(normalized)) {
            throw new IllegalArgumentException("安全校验失败：SQL 不允许包含多条语句");
        }

        for (String keyword : DANGEROUS_KEYWORDS) {
            if (Pattern.compile("\\b" + keyword + "\\b").matcher(upperSql).find()) {
                throw new IllegalArgumentException("安全校验失败：SQL 包含禁止的关键字: " + keyword);
            }
        }

        validateTableReferences(normalized);
        validateMainSelectClause(normalized);
        validateLargeTableAccess(normalized);
        return enforceResultLimit(normalized);
    }

    private static boolean containsComment(String sql) {
        return sql.contains("--") || sql.contains("/*") || sql.contains("*/");
    }

    private static boolean containsMultipleStatements(String sql) {
        int semicolonIndex = sql.indexOf(';');
        return semicolonIndex >= 0 && semicolonIndex < sql.length() - 1;
    }

    private static String stripTrailingSemicolons(String sql) {
        int end = sql.length();
        while (end > 0 && Character.isWhitespace(sql.charAt(end - 1))) {
            end--;
        }
        while (end > 0 && sql.charAt(end - 1) == ';') {
            end--;
            while (end > 0 && Character.isWhitespace(sql.charAt(end - 1))) {
                end--;
            }
        }
        return sql.substring(0, end).trim();
    }

    private static void validateTableReferences(String sql) {
        Set<String> allowedRefs = new HashSet<>(ALLOWED_TABLES);
        allowedRefs.addAll(extractCteNames(sql));

        Matcher matcher = TABLE_REF_PATTERN.matcher(sql);
        while (matcher.find()) {
            String ref = normalizeRef(matcher.group(1));
            if (ref.isEmpty()) {
                continue;
            }
            if (allowedRefs.contains(ref) || isPartitionTable(ref)) {
                continue;
            }
            throw new IllegalArgumentException("安全校验失败：SQL 访问了未授权的表/视图: " + ref);
        }
    }

    private static void validateMainSelectClause(String sql) {
        int selectIndex = findTopLevelTokenIndex(sql, "SELECT", 0);
        int fromIndex = findTopLevelTokenIndex(sql, "FROM", selectIndex + "SELECT".length());
        if (selectIndex < 0 || fromIndex < 0 || fromIndex <= selectIndex) {
            throw new IllegalArgumentException("安全校验失败：SQL 结构不完整");
        }

        String selectClause = sql.substring(selectIndex + "SELECT".length(), fromIndex).trim();
        String normalizedClause = selectClause
                .replaceFirst("(?i)^DISTINCT\\s+", "")
                .replaceFirst("(?i)^TOP\\s*\\(?\\s*\\d+\\s*\\)?\\s+", "")
                .trim();

        if (STANDALONE_WILDCARD_PATTERN.matcher(normalizedClause).find()) {
            throw new IllegalArgumentException("安全校验失败：禁止 SELECT * 或表别名.*");
        }
    }

    private static void validateLargeTableAccess(String sql) {
        if (!touchesLargeHealthTable(sql)) {
            return;
        }
        if (hasTimeFilter(sql)) {
            return;
        }
        if (isSmallRecentQuery(sql)) {
            return;
        }
        throw new IllegalArgumentException("安全校验失败：健康记录或预警明细查询必须带时间范围");
    }

    private static boolean touchesLargeHealthTable(String sql) {
        Matcher matcher = TABLE_REF_PATTERN.matcher(sql);
        while (matcher.find()) {
            String ref = normalizeRef(matcher.group(1));
            if ("V_HEALTH_RECORD".equals(ref)
                    || "V_WARNING_RECORD".equals(ref)
                    || ref.matches("HEALTH_RECORD_\\d{6}")
                    || ref.matches("WARNING_RECORD_\\d{6}")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTimeFilter(String sql) {
        for (Pattern pattern : TIME_FILTER_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSmallRecentQuery(String sql) {
        int selectIndex = findTopLevelTokenIndex(sql, "SELECT", 0);
        int fromIndex = findTopLevelTokenIndex(sql, "FROM", selectIndex + "SELECT".length());
        if (selectIndex < 0 || fromIndex < 0) {
            return false;
        }

        String selectClause = sql.substring(selectIndex + "SELECT".length(), fromIndex);
        Integer topLimit = extractTopLimit(selectClause);
        return topLimit != null
                && topLimit <= MAX_RECENT_TOP_QUERY_ROWS
                && ORDER_BY_TIME_PATTERN.matcher(sql).find()
                && SMALL_SCOPE_FILTER_PATTERN.matcher(sql).find();
    }

    private static String enforceResultLimit(String sql) {
        int selectIndex = findTopLevelTokenIndex(sql, "SELECT", 0);
        int fromIndex = findTopLevelTokenIndex(sql, "FROM", selectIndex + "SELECT".length());
        if (selectIndex < 0 || fromIndex < 0) {
            return sql;
        }

        String selectClause = sql.substring(selectIndex + "SELECT".length(), fromIndex);
        Integer topLimit = extractTopLimit(selectClause);
        if (topLimit != null) {
            if (topLimit <= MAX_RESULT_ROWS) {
                return sql;
            }
            return clampTopLimit(sql, selectIndex);
        }
        return injectTopLimit(sql, selectIndex);
    }

    private static Integer extractTopLimit(String selectClause) {
        Matcher matcher = LEADING_TOP_PATTERN.matcher(selectClause);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return null;
    }

    private static String clampTopLimit(String sql, int selectIndex) {
        String prefix = sql.substring(0, selectIndex + "SELECT".length());
        String rest = sql.substring(selectIndex + "SELECT".length());
        Matcher matcher = LEADING_TOP_PATTERN.matcher(rest);
        if (!matcher.find()) {
            return sql;
        }
        String distinct = matcher.group(1) == null ? "" : matcher.group(1);
        return prefix + matcher.replaceFirst(" " + distinct + "TOP " + MAX_RESULT_ROWS + " ");
    }

    private static String injectTopLimit(String sql, int selectIndex) {
        String rest = sql.substring(selectIndex + "SELECT".length());
        Matcher distinctMatcher = LEADING_DISTINCT_PATTERN.matcher(rest);
        if (distinctMatcher.find()) {
            int insertPos = selectIndex + "SELECT".length() + distinctMatcher.end();
            return sql.substring(0, insertPos) + " TOP " + MAX_RESULT_ROWS + sql.substring(insertPos);
        }
        int insertPos = selectIndex + "SELECT".length();
        return sql.substring(0, insertPos) + " TOP " + MAX_RESULT_ROWS + sql.substring(insertPos);
    }

    private static Set<String> extractCteNames(String sql) {
        Set<String> cteNames = new HashSet<>();
        if (!sql.trim().toUpperCase(Locale.ROOT).startsWith("WITH")) {
            return cteNames;
        }
        Matcher matcher = CTE_NAME_PATTERN.matcher(sql);
        while (matcher.find()) {
            cteNames.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return cteNames;
    }

    private static String normalizeRef(String ref) {
        if (ref == null) {
            return "";
        }
        String normalized = ref.replace("[", "").replace("]", "");
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isPartitionTable(String ref) {
        return ref.matches("HEALTH_RECORD_\\d{6}") || ref.matches("WARNING_RECORD_\\d{6}");
    }

    private static int findTopLevelTokenIndex(String sql, String token, int startIndex) {
        int depth = 0;
        boolean inString = false;
        for (int i = Math.max(0, startIndex); i <= sql.length() - token.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'') {
                if (inString && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0 && matchesToken(sql, i, token)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesToken(String sql, int start, String token) {
        int end = start + token.length();
        if (end > sql.length() || !sql.regionMatches(true, start, token, 0, token.length())) {
            return false;
        }
        boolean leftBoundary = start == 0 || !Character.isLetterOrDigit(sql.charAt(start - 1));
        boolean rightBoundary = end == sql.length() || !Character.isLetterOrDigit(sql.charAt(end));
        return leftBoundary && rightBoundary;
    }
}
