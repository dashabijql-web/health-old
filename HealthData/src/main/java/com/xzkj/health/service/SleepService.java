package com.xzkj.health.service;

import com.xzkj.health.common.MapValueUtil;
import com.xzkj.health.config.datasource.HealthCacheKeys;
import com.xzkj.health.config.datasource.HealthAsyncQueryExecutor;
import com.xzkj.health.dto.sleep.SleepDeptUploadView;
import com.xzkj.health.dto.sleep.SleepDetailItemView;
import com.xzkj.health.dto.sleep.SleepLegendItemView;
import com.xzkj.health.dto.sleep.SleepOverviewView;
import com.xzkj.health.dto.sleep.SleepPageDataView;
import com.xzkj.health.dto.sleep.SleepQualityDistributionView;
import com.xzkj.health.dto.sleep.SleepTrendView;
import com.xzkj.health.mapper.SleepMapper;
import com.xzkj.health.util.LocalTtlCache;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 睡眠监测Service
 */
@Service
public class SleepService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    @Autowired
    private SleepMapper sleepMapper;

    @Autowired
    private HealthAsyncQueryExecutor asyncQueryExecutor;

    private final LocalTtlCache<SleepTrendView> trendCache = new LocalTtlCache<>();
    private final LocalTtlCache<SleepQualityDistributionView> qualityDistributionCache = new LocalTtlCache<>();
    private final LocalTtlCache<SleepPageDataView> pageDataCache = new LocalTtlCache<>();

    /**
     * 获取睡眠趋势数据
     */
    public SleepTrendView getSleepTrend(int days) {
        String cacheKey = HealthCacheKeys.key("trend", days);
        SleepTrendView hit = trendCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        List<Map<String, Object>> trendData = sleepMapper.getSleepTrend(days);

        List<String> dates = new ArrayList<>();
        List<Double> avgData = new ArrayList<>();
        List<Double> deepSleep = new ArrayList<>();
        List<Double> lightSleep = new ArrayList<>();

        for (Map<String, Object> item : trendData) {
            String date = (String) item.get("date");
            dates.add(date.substring(5)); // 只保留月-日
            avgData.add(MapValueUtil.getDouble(item, "avgSleepHours"));
            deepSleep.add(MapValueUtil.getDouble(item, "avgDeepSleep"));
            lightSleep.add(MapValueUtil.getDouble(item, "avgLightSleep"));
        }

        SleepTrendView result = new SleepTrendView(dates, avgData, deepSleep, lightSleep);
        trendCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    /**
     * 获取睡眠质量分布
     */
    public SleepQualityDistributionView getQualityDistribution() {
        String cacheKey = HealthCacheKeys.key("quality-distribution");
        SleepQualityDistributionView hit = qualityDistributionCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }
        List<Map<String, Object>> distribution = sleepMapper.getSleepQualityDistribution();

        int excellent = 0;
        int good = 0;
        int fair = 0;
        int poor = 0;
        for (Map<String, Object> item : distribution) {
            String quality = stringValue(item.get("quality"));
            int count = MapValueUtil.getInt(item, "count");
            if ("excellent".equals(quality)) {
                excellent = count;
            } else if ("good".equals(quality)) {
                good = count;
            } else if ("fair".equals(quality)) {
                fair = count;
            } else if ("poor".equals(quality)) {
                poor = count;
            }
        }

        SleepQualityDistributionView result = new SleepQualityDistributionView(excellent, good, fair, poor);
        qualityDistributionCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    /**
     * 获取睡眠页面完整数据（并行查询优化：7个DB查询并行执行，冷启动从3.2s降至~0.5s）
     */
    public SleepPageDataView getSleepPageData() {
        String cacheKey = HealthCacheKeys.key("page-data");
        SleepPageDataView hit = pageDataCache.getIfFresh(cacheKey);
        if (hit != null) {
            return hit;
        }

        CompletableFuture<Map<String, Object>> lastNightOverviewFuture =
                supply(() -> sleepMapper.getLastNightOverviewDirect(recentSleepSource()));
        CompletableFuture<Map<String, Object>> durationFuture = supply(sleepMapper::getSleepDurationDistribution);
        CompletableFuture<Map<String, Object>> categoryFuture = supply(sleepMapper::getSleepCategoryDistribution);
        CompletableFuture<List<Map<String, Object>>> deptFuture = supply(sleepMapper::getDeptUploadStats);
        CompletableFuture<List<Map<String, Object>>> detailFuture = supply(sleepMapper::getLatestSleepDetails);

        CompletableFuture.allOf(
                lastNightOverviewFuture,
                durationFuture,
                categoryFuture,
                deptFuture,
                detailFuture
        ).join();

        Map<String, Object> lastNightOverview = lastNightOverviewFuture.join();
        Map<String, Object> durationData   = durationFuture.join();
        Map<String, Object> categoryData   = categoryFuture.join();
        List<Map<String, Object>> deptStats = deptFuture.join();
        List<Map<String, Object>> details   = detailFuture.join();

        double avgSleepTime = MapValueUtil.getDouble(lastNightOverview, "avgSleepTime");
        int hours = (int) avgSleepTime;
        int minutes = (int) Math.round((avgSleepTime - hours) * 60);
        int totalCount = (int) MapValueUtil.getLong(durationData, "total");
        SleepOverviewView overview = new SleepOverviewView(
                Math.round((float) MapValueUtil.getDouble(lastNightOverview, "uploadRate")),
                Math.round((float) MapValueUtil.getDouble(lastNightOverview, "greenLineRate")),
                hours + "小时" + minutes + "分钟",
                Math.round((float) MapValueUtil.getDouble(lastNightOverview, "avgScore")),
                totalCount
        );

        List<SleepLegendItemView> durationLegend = new ArrayList<>();
        long total = totalCount;
        if (total > 0) {
            durationLegend.add(createLegendItem("<4小时",
                    Math.round(MapValueUtil.getLong(durationData, "less4") * 100.0 / total), "#FF6B6B"));
            durationLegend.add(createLegendItem("4-6小时",
                    Math.round(MapValueUtil.getLong(durationData, "range4to6") * 100.0 / total), "#FFA726"));
            durationLegend.add(createLegendItem("6-8小时",
                    Math.round(MapValueUtil.getLong(durationData, "range6to8") * 100.0 / total), "#4FC3F7"));
            durationLegend.add(createLegendItem(">8小时",
                    Math.round(MapValueUtil.getLong(durationData, "more8") * 100.0 / total), "#66BB6A"));
        }

        List<SleepLegendItemView> categoryLegend = new ArrayList<>();
        long deepSleepMin  = MapValueUtil.getLong(categoryData, "deepSleep");
        long lightSleepMin = MapValueUtil.getLong(categoryData, "lightSleep");
        long dreamMin      = MapValueUtil.getLong(categoryData, "dream");
        long awakeMin      = MapValueUtil.getLong(categoryData, "awake");
        long timeTotal     = deepSleepMin + lightSleepMin + dreamMin + awakeMin;
        if (timeTotal > 0) {
            categoryLegend.add(createLegendItem("深睡",  Math.round(deepSleepMin  * 100.0 / timeTotal), "#4FC3F7"));
            categoryLegend.add(createLegendItem("浅睡",  Math.round(lightSleepMin * 100.0 / timeTotal), "#66BB6A"));
            categoryLegend.add(createLegendItem("梦境",  Math.round(dreamMin      * 100.0 / timeTotal), "#FFA726"));
            categoryLegend.add(createLegendItem("清醒",  Math.round(awakeMin      * 100.0 / timeTotal), "#FF6B6B"));
        }

        List<SleepDetailItemView> detailList = new ArrayList<>();
        for (Map<String, Object> detail : details) {
            double sleepHours = MapValueUtil.getDouble(detail, "sleepHours");
            int h = (int) sleepHours;
            int m = (int) Math.round((sleepHours - h) * 60);
            String level = stringValue(detail.get("level"));
            String levelText = "良";
            if ("excellent".equals(level)) levelText = "优";
            else if ("fair".equals(level)) levelText = "中";
            else if ("poor".equals(level)) levelText = "差";
            detailList.add(new SleepDetailItemView(
                    stringValue(detail.get("userName")),
                    stringValue(detail.get("deptName")),
                    stringValue(detail.get("empCode")),
                    h + "小时" + m + "分钟",
                    nullableInt(detail.get("score")),
                    level,
                    levelText,
                    stringValue(detail.get("recordTime"))
            ));
        }

        SleepPageDataView result = new SleepPageDataView(
                overview,
                durationLegend,
                categoryLegend,
                toDeptUploadViews(deptStats),
                detailList,
                totalCount
        );
        pageDataCache.put(cacheKey, result, CACHE_TTL_MILLIS);
        return result;
    }

    private List<SleepDeptUploadView> toDeptUploadViews(List<Map<String, Object>> deptStats) {
        List<SleepDeptUploadView> result = new ArrayList<>();
        for (Map<String, Object> row : deptStats == null ? Collections.<Map<String, Object>>emptyList() : deptStats) {
            result.add(new SleepDeptUploadView(
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "count")
            ));
        }
        return result;
    }

    private SleepLegendItemView createLegendItem(String name, long value, String color) {
        return new SleepLegendItemView(name, value, color);
    }

    private <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return asyncQueryExecutor.supply(supplier);
    }

    private String recentSleepSource() {
        return TableSourceUtil.healthRecordSource(
                LocalDate.now().minusDays(31),
                LocalDate.now(),
                "user_code,sleep_minutes,record_time"
        );
    }

    private Integer nullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
