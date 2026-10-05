package com.bbh.itss.dso.portal.adapter.in.web;

final class InputFormats {

    static final String URL = "^(https?://\\S+)?$";
    static final String URL_MESSAGE = "must be an http or https URL";
    static final String HOST = "^[A-Za-z0-9.-]*$";
    static final String HOST_MESSAGE = "must be a host name";

    private InputFormats() {
    }
}
