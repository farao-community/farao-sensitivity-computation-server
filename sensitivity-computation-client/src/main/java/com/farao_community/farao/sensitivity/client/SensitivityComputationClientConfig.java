/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.client;

import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.PlatformConfig;

import java.util.Optional;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
public class SensitivityComputationClientConfig {
    private static final String DEFAULT_BASE_URL = "http://localhost:8080/";
    private final String baseUrl;

    public SensitivityComputationClientConfig(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public SensitivityComputationClientConfig() {
        this(DEFAULT_BASE_URL);
    }

    public static SensitivityComputationClientConfig load() {
        Optional<ModuleConfig> moduleConfig = PlatformConfig.defaultConfig()
                .getOptionalModuleConfig("sensitivity-computation-client");
        String baseUrl = moduleConfig.flatMap(mc -> mc.getOptionalStringProperty("base-url")).orElse(DEFAULT_BASE_URL);
        return new SensitivityComputationClientConfig(baseUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
