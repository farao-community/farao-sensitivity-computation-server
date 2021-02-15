/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;

import java.util.List;
import java.util.Map;

public class InternalSensitivityInputsProvider implements SensitivityFactorsProvider {
    @JsonProperty("commonFactors")
    private final List<SensitivityFactor> commonFactors;
    @JsonProperty("basecaseAdditionalFactors")
    private final List<SensitivityFactor> basecaseAdditionalFactors;
    @JsonProperty("contingenciesAdditionalParameters")
    private final Map<String, List<SensitivityFactor>> contingenciesAdditionalParameters;
    @JsonProperty("contingencies")
    private final List<Contingency> contingencies;

    @JsonCreator
    public InternalSensitivityInputsProvider(@JsonProperty("commonFactors") List<SensitivityFactor> commonFactors,
                                             @JsonProperty("basecaseAdditionalFactors") List<SensitivityFactor> basecaseAdditionalFactors,
                                             @JsonProperty("contingenciesAdditionalParameters") Map<String, List<SensitivityFactor>> contingenciesAdditionalParameters,
                                             @JsonProperty("contingencies") List<Contingency> contingencies) {
        this.commonFactors = commonFactors;
        this.basecaseAdditionalFactors = basecaseAdditionalFactors;
        this.contingenciesAdditionalParameters = contingenciesAdditionalParameters;
        this.contingencies = contingencies;
    }

    public List<Contingency> getContingencies() {
        return contingencies;
    }

    @Override
    public List<SensitivityFactor> getCommonFactors(Network network) {
        return commonFactors;
    }

    @Override
    public List<SensitivityFactor> getAdditionalFactors(Network network) {
        return basecaseAdditionalFactors;
    }

    @Override
    public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
        return contingenciesAdditionalParameters.get(contingencyId);
    }
}
