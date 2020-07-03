/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.NetworkXml;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.json.JsonSensitivityComputationParameters;
import com.powsybl.sensitivity.json.SensitivityComputationResultJsonSerializer;
import com.powsybl.sensitivity.json.SensitivityFactorsJsonSerializer;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
public class SensitivityComputationClient implements SensitivityComputation {
    private final SensitivityComputationClientConfig config;
    private final Network network;

    SensitivityComputationClient(Network network, SensitivityComputationClientConfig config) {
        this.network = network;
        this.config = config;
    }

    @Override
    public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider sensitivityFactorsProvider, String workingStateId, SensitivityComputationParameters sensitivityComputationParameters) {
        return run(sensitivityFactorsProvider, null, workingStateId, sensitivityComputationParameters);
    }

    @Override
    public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider factorsProvider, ContingenciesProvider contingenciesProvider, String workingStateId, SensitivityComputationParameters sensiParameters) {
        WebClient webClient = WebClient.create();
        byte[] resultBytes = webClient.post()
                .uri(getServerUri())
                .bodyValue(createBody(workingStateId, factorsProvider, contingenciesProvider, sensiParameters))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        return CompletableFuture.completedFuture(parseResults(resultBytes));
    }

    @Override
    public String getName() {
        return "SensitivityComputationClient";
    }

    @Override
    public String getVersion() {
        return "0.0.1";
    }

    private SensitivityComputationResults parseResults(byte[] resultBytes) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(resultBytes);
            Reader reader = new InputStreamReader(inputStream);
            return SensitivityComputationResultJsonSerializer.read(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private URI getServerUri() {
        return URI.create(config.getBaseUrl())
                .resolve("./api/v1/sensitivity-computation");
    }

    private MultiValueMap<String, HttpEntity<?>> createBody(String workingStateId, SensitivityFactorsProvider factorsProvider, ContingenciesProvider contingenciesProvider, SensitivityComputationParameters sensiParameters) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("networkFile", getNetworkBytes(network, workingStateId), MediaType.APPLICATION_XML).filename("network.xiidm");
        builder.part("sensitivityFactorsFile", getFactorsBytes(factorsProvider), MediaType.APPLICATION_JSON).filename("sensitivityFactors.json");
        builder.part("contingencyListFile", getContingenciesBytes(contingenciesProvider), MediaType.APPLICATION_JSON).filename("contingencyList.json");
        builder.part("parametersFile", getParametersBytes(sensiParameters), MediaType.APPLICATION_JSON).filename("parameters.json");
        return builder.build();
    }

    private byte[] getNetworkBytes(Network network, String workingStateId) {
        String initialVariant = network.getVariantManager().getWorkingVariantId();
        network.getVariantManager().setWorkingVariant(workingStateId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NetworkXml.write(network, baos);
        network.getVariantManager().setWorkingVariant(initialVariant);
        return baos.toByteArray();
    }

    private byte[] getFactorsBytes(SensitivityFactorsProvider factorsProvider) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(baos);
            SensitivityFactorsJsonSerializer.write(factorsProvider.getFactors(network), writer);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] getContingenciesBytes(ContingenciesProvider contingenciesProvider) {
        try {
            List<Contingency> contingencies = contingenciesProvider == null ? Collections.emptyList() : contingenciesProvider.getContingencies(network);
            ObjectMapper mapper = JsonUtil.createObjectMapper();
            mapper.registerModule(new ContingencyJsonModule());
            return mapper.writeValueAsBytes(contingencies);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] getParametersBytes(SensitivityComputationParameters sensiParameters) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonSensitivityComputationParameters.write(sensiParameters, baos);
        return baos.toByteArray();
    }
}
