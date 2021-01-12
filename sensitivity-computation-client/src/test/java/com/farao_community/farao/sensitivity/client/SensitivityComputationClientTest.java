/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.client;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
class SensitivityComputationClientTest {
    @Disabled
    @Test
    public void checkThatClientsWorkLocally() {
        Network network = Importers.loadNetwork("20170215_0830_2d4_uc1.uct", getClass().getResourceAsStream("/20170215_0830_2d4_uc1.uct"));

        ContingenciesProvider contingenciesProvider = networkInput -> networkInput.getBranchStream().map(branch -> new Contingency(branch.getId(), new BranchContingency(branch.getId()))).collect(Collectors.toList());

        SensitivityAnalysisResult result = SensitivityAnalysis.find("SensitivityComputationClient").run(network, new mockProvider(), contingenciesProvider.getContingencies(network));

        System.out.println(result.isOk());
    }

    public class mockProvider implements SensitivityFactorsProvider {
        @Override
        public List<SensitivityFactor> getFactors(Network network) {
            List<SensitivityFactor> sensitivityFactors = new ArrayList<>();
            for (Branch<?> branch : network.getBranches()) {
                for (Load load : network.getLoads()) {
                    sensitivityFactors.add(new BranchFlowPerInjectionIncrease(new BranchFlow(branch.getId(), branch.getId(), branch.getId()), new InjectionIncrease(load.getId(), load.getId(), load.getId())));
                }
            }
            return sensitivityFactors;
        }

        @Override
        public List<SensitivityFactor> getFactors(Network network, String s) {
            return getFactors(network);
        }
    }
}