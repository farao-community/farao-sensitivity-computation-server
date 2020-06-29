/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.config.ComponentDefaultConfig;
import com.powsybl.computation.DefaultComputationManagerConfig;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.converter.SensitivityComputationResultExporters;
import com.powsybl.sensitivity.json.JsonSensitivityComputationParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
@Service
public class SensitivityComputationServerService {
    public byte[] runComputation(MultipartFile networkFile, MultipartFile sensitivityFactorsFile, MultipartFile contingencyListFile, MultipartFile parametersFile) {
        Network network = importNetwork(networkFile);
        SensitivityFactorsProvider sensitivityFactorsProvider = importSensitivityFactorsProvider(sensitivityFactorsFile);
        ContingenciesProvider contingencies = importContingenciesProvider(contingencyListFile);
        SensitivityComputationParameters parameters = importParameters(parametersFile);

        SensitivityComputationFactory sensitivityComputationFactory = ComponentDefaultConfig.load().newFactoryImpl(SensitivityComputationFactory.class);
        SensitivityComputation sensitivityComputation = sensitivityComputationFactory.create(network, DefaultComputationManagerConfig.load().createLongTimeExecutionComputationManager(), 1);
        return sensitivityComputation.run(sensitivityFactorsProvider, contingencies, network.getVariantManager().getWorkingVariantId(), parameters)
                .thenApply(this::turnToBytes).join();
    }

    private Network importNetwork(MultipartFile networkFile) {
        try {
            return Importers.loadNetwork(networkFile.getOriginalFilename(), networkFile.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SensitivityFactorsProvider importSensitivityFactorsProvider(MultipartFile sensitivityFactorsFile) {
        try {
            JsonSensitivityFactorsProviderFactory factory = new JsonSensitivityFactorsProviderFactory();
            return factory.create(sensitivityFactorsFile.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ContingenciesProvider importContingenciesProvider(MultipartFile contingencyListFile) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new ContingencyJsonModule());
            TypeReference<List<Contingency>> mapType = new TypeReference<List<Contingency>>() {};
            List<Contingency> contingencyList = mapper.readValue(contingencyListFile.getInputStream(), mapType);
            return new ContingenciesProvider() {
                @Override
                public List<Contingency> getContingencies(Network network) {
                    return contingencyList;
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SensitivityComputationParameters importParameters(MultipartFile parametersFile) {
        try {
            return JsonSensitivityComputationParameters.read(parametersFile.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] turnToBytes(SensitivityComputationResults sensitivityComputationResults) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(baos);
        SensitivityComputationResultExporters.export(sensitivityComputationResults, writer, "JSON");
        return baos.toByteArray();
    }
}
