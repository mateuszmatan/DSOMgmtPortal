package com.bbh.itss.dso.portal.application.monitoring.port.out;

public interface MonitoringStatusPort {

    boolean configured();

    void ping();
}
