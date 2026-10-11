package com.gole.api.admin.domain.model;

import java.util.Map;

public record AdminOrderStats(Map<String, Long> countByStatus, long completedGmv) {}
