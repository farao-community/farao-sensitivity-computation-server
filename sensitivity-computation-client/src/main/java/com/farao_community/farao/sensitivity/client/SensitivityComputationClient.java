/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.NetworkXml;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;
import com.powsybl.sensitivity.json.SensitivityAnalysisResultJsonSerializer;
import com.powsybl.sensitivity.json.SensitivityFactorsJsonSerializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
@AutoService(SensitivityAnalysisProvider.class)
public class SensitivityComputationClient implements SensitivityAnalysisProvider {
    private final SensitivityComputationClientConfig config;

    public SensitivityComputationClient() {
        this(SensitivityComputationClientConfig.fromPropertyFile());
    }

    public SensitivityComputationClient(SensitivityComputationClientConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<SensitivityAnalysisResult> run(Network network, String workingVariantId, SensitivityFactorsProvider factorsProvider, List<Contingency> contingencies, SensitivityAnalysisParameters sensiParameters, ComputationManager computationManager) {
        TcpClient timeoutClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getTimeOutInSeconds()*1000)
            .doOnConnected(
                c -> c.addHandlerLast(new ReadTimeoutHandler(config.getTimeOutInSeconds()*1000))
                    .addHandlerLast(new WriteTimeoutHandler(config.getTimeOutInSeconds()*1000)));

        WebClient webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.from(timeoutClient)))
            .build();

        //WebClient webClient = WebClient.create();
        Flux<DataBuffer> resultData = webClient.post()
                .uri(getServerUri())
                .bodyValue(createBody(network, workingVariantId, factorsProvider, contingencies, sensiParameters))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofMillis(config.getTimeOutInSeconds()*1000));

        return CompletableFuture.completedFuture(parseResults(resultData));
    }

    @Override
    public String getName() {
        return "SensitivityComputationClient";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    private SensitivityAnalysisResult parseResults(Flux<DataBuffer> resultData) {
        try {
            Reader reader = new InputStreamReader(DataBufferUtils.join(resultData).block().asInputStream());
            return SensitivityAnalysisResultJsonSerializer.read(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private URI getServerUri() {
        return URI.create(config.getBaseUrl())
                .resolve("./api/v1/sensitivity-computation");
    }

    private MultiValueMap<String, HttpEntity<?>> createBody(Network network, String workingStateId, SensitivityFactorsProvider factorsProvider, List<Contingency> contingencies, SensitivityAnalysisParameters sensiParameters) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("networkFile", getNetworkBytes(network, workingStateId), MediaType.APPLICATION_XML).filename("network.xiidm");
        builder.part("sensitivityFactorsFile", getFactorsBytes(network, factorsProvider), MediaType.APPLICATION_JSON).filename("sensitivityFactors.json");
        builder.part("contingencyListFile", getContingenciesBytes(contingencies), MediaType.APPLICATION_JSON).filename("contingencyList.json");
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

    private byte[] getFactorsBytes(Network network, SensitivityFactorsProvider factorsProvider) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(baos);
            SensitivityFactorsJsonSerializer.write(factorsProvider.getFactors(network), writer);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] getContingenciesBytes(List<Contingency> contingencies) {
        try {
            ObjectMapper mapper = JsonUtil.createObjectMapper();
            mapper.registerModule(new ContingencyJsonModule());
            return mapper.writeValueAsBytes(contingencies);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] getParametersBytes(SensitivityAnalysisParameters sensiParameters) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonSensitivityAnalysisParameters.write(sensiParameters, baos);
        return baos.toByteArray();
    }
}
