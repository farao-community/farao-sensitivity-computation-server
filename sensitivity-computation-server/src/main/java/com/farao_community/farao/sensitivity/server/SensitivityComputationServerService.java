/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.server;

import com.farao_community.farao.sensitivity.api.InternalSensitivityInputsProvider;
import com.farao_community.farao.sensitivity.api.JsonSensitivityInputs;
import com.farao_community.farao.sensitivity.api.JsonSensitivityOutputs;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.converter.SensitivityAnalysisResultExporters;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.*;
import java.util.List;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
@Service
public class SensitivityComputationServerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityComputationServerService.class);

    public Flux<DataBuffer> runComputation(FilePart networkFile, FilePart inputsFile, FilePart parametersFile) {
        LOGGER.info("[start] sensitivity computation");
        Network network = importNetwork(networkFile);
        InternalSensitivityInputsProvider inputsProvider = importSensitivityInputsProvider(inputsFile);
        SensitivityAnalysisParameters parameters = importParameters(parametersFile);
        SensitivityAnalysisResult result = SensitivityAnalysis.run(network, inputsProvider, (List<Contingency>) inputsProvider.getContingencies(), parameters);
        LOGGER.info("[end] sensitivity computation");
        return turnToData(result);
    }

    private Network importNetwork(FilePart networkFile) {
        return DataBufferUtils.join(networkFile.content())
                .map(dataBuffer -> Importers.loadNetwork(networkFile.filename(), dataBuffer.asInputStream())).toFuture().join();
    }

    private InternalSensitivityInputsProvider importSensitivityInputsProvider(FilePart inputsFile) {
        return DataBufferUtils.join(inputsFile.content())
                .map(dataBuffer -> JsonSensitivityInputs.read(dataBuffer.asInputStream())).toFuture().join();
    }

    private SensitivityAnalysisParameters importParameters(FilePart parametersFile) {
        return DataBufferUtils.join(parametersFile.content())
                .map(dataBuffer -> JsonSensitivityAnalysisParameters.read(dataBuffer.asInputStream())).toFuture().join();
    }

    private Flux<DataBuffer> turnToData(SensitivityAnalysisResult sensitivityComputationResults) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(byteArrayOutputStream, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        JsonSensitivityOutputs.write(sensitivityComputationResults, writer);
        return DataBufferUtils.readInputStream(() -> new ByteArrayInputStream(byteArrayOutputStream.toByteArray()), new DefaultDataBufferFactory(), 1024);
    }
}
