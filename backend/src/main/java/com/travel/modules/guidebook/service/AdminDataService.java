package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.WeatherDataProvider;
import com.travel.modules.guidebook.dto.DataSourceConfigItem;
import com.travel.modules.guidebook.dto.UpdateDataSourceRequest;
import com.travel.modules.guidebook.entity.DataSourceConfig;
import com.travel.modules.guidebook.mapper.DataSourceConfigMapper;

import lombok.RequiredArgsConstructor;

/** 运营侧数据源配置与实时数据查询。 */
@Service
@RequiredArgsConstructor
public class AdminDataService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DataSourceConfigMapper configMapper;
    private final WeatherDataProvider weatherProvider;

    public List<DataSourceConfigItem> listSources() {
        return configMapper.selectList(null).stream()
                .sorted(Comparator.comparingInt(config -> config.getPriority() == null
                        ? Integer.MAX_VALUE : config.getPriority()))
                .map(this::toItem)
                .toList();
    }

    public DataSourceConfigItem updateSource(String sourceId, UpdateDataSourceRequest request) {
        DataSourceConfig config = configMapper.selectById(sourceId);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源不存在: " + sourceId);
        }
        if (request != null) {
            if (request.getEnabled() != null) {
                config.setEnabled(request.getEnabled());
            }
            if (request.getPriority() != null) {
                config.setPriority(request.getPriority());
            }
            if (request.getCacheTtlSeconds() != null) {
                config.setCacheTtlSeconds(Math.max(0, request.getCacheTtlSeconds()));
            }
        }
        configMapper.updateById(config);
        return toItem(config);
    }

    public DataBlock weather(String destination, String date) {
        if (!StringUtils.hasText(destination) || !StringUtils.hasText(date)) {
            throw new BizException(ResultCode.PARAM_ERROR, "destination 与 date 不能为空");
        }
        IntentDto intent = new IntentDto();
        intent.setDestination(destination);
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(date);
        window.setEnd(date);
        intent.setDateWindow(window);
        return weatherProvider.fetch(intent)
                .orElseGet(() -> DataBlock.unavailable("weather",
                        LocalDateTime.now().format(TS)));
    }

    private DataSourceConfigItem toItem(DataSourceConfig config) {
        return new DataSourceConfigItem(config.getSourceId(), config.getEnabled(),
                config.getPriority(), config.getCacheTtlSeconds(), config.getConfig());
    }
}
