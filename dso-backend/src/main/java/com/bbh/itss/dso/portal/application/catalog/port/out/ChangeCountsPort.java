package com.bbh.itss.dso.portal.application.catalog.port.out;

import java.util.Map;

public interface ChangeCountsPort {

    Map<Long, Long> changesPerDepartment();
}
