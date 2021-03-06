package com.farao_community.farao.sensitivity.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityFactorsProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

public class JsonSensitivityInputs {
    private JsonSensitivityInputs() {
        throw new AssertionError("Utility class should not be implemented");
    }

    public static byte[] write(SensitivityFactorsProvider provider, Network network, List<Contingency> contingencies) {
        try {
            InternalSensitivityInputsProvider internalProvider = new InternalSensitivityInputsProvider(
                    provider.getCommonFactors(network),
                    provider.getAdditionalFactors(network),
                    contingencies.stream().collect(Collectors.toMap(Contingency::getId, co -> provider.getAdditionalFactors(network, co.getId()))),
                    contingencies
            );
            return getObjectMapper().writeValueAsBytes(internalProvider);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static InternalSensitivityInputsProvider read(InputStream inputStream) {
        try {
            return getObjectMapper().readValue(inputStream, InternalSensitivityInputsProvider.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectMapper getObjectMapper() {
        return new ObjectMapper().registerModule(new ContingencyJsonModule());
    }
}
