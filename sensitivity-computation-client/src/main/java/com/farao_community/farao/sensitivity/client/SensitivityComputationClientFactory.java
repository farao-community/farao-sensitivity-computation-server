/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.client;

import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityComputation;
import com.powsybl.sensitivity.SensitivityComputationFactory;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
public class SensitivityComputationClientFactory implements SensitivityComputationFactory {
    private SensitivityComputationClientConfig config;

    public SensitivityComputationClientFactory() {
        this(SensitivityComputationClientConfig.load());
    }

    public SensitivityComputationClientFactory(SensitivityComputationClientConfig config) {
        this.config = config;
    }

    public SensitivityComputation create(Network network, ComputationManager computationManager, int i) {
        return new SensitivityComputationClient(network, config);
    }
}
