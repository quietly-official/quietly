package ua.quietlycore.model;

import java.util.List;

public record FilterEntityInfo(Class<?> entityClass, List<FilterInfo> filters) {
}