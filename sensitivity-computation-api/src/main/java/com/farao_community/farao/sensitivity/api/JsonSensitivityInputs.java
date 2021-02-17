package com.farao_community.farao.sensitivity.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.SensitivityFunction;
import com.powsybl.sensitivity.SensitivityVariable;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class JsonSensitivityInputs {
    private JsonSensitivityInputs() {
        throw new AssertionError("Utility class should not be implemented");
    }

    public static byte[] write(SensitivityFactorsProvider provider, Network network, List<Contingency> contingencies) {
        Map<String, SensitivityVariable> sensitivityVariableMap = new LinkedHashMap<>();
        Map<String, SensitivityFunction> sensitivityFunctionMap = new LinkedHashMap<>();
        Map<Contingency, Set<String>> sensitivityFunctionStringMap = new LinkedHashMap<>();
        for (Contingency contingency : contingencies) {
            String contingencyId = contingency.getId();
            Set<String> sensitivityFunctionsString = new LinkedHashSet<>();
            List<SensitivityFactor> sensitivities = provider.getAdditionalFactors(network, contingencyId);
            for (SensitivityFactor sensitivityFactor : sensitivities) {
                sensitivityFunctionsString.add(sensitivityFactor.getFunction().getId());
                sensitivityVariableMap.put(sensitivityFactor.getVariable().getId(), sensitivityFactor.getVariable());
                sensitivityFunctionMap.put(sensitivityFactor.getFunction().getId(), sensitivityFactor.getFunction());
            }
            sensitivityFunctionStringMap.put(contingency, sensitivityFunctionsString);
        }

        List<SensitivityFactor> basecaseSensitivities = provider.getAdditionalFactors(network);
        Set<String> basecaseSensitivityFunctions = new LinkedHashSet<>();
        for (SensitivityFactor sensitivityFactor : basecaseSensitivities) {
            basecaseSensitivityFunctions.add(sensitivityFactor.getFunction().getId());
            sensitivityVariableMap.put(sensitivityFactor.getVariable().getId(), sensitivityFactor.getVariable());
            sensitivityFunctionMap.put(sensitivityFactor.getFunction().getId(), sensitivityFactor.getFunction());
        }


        try {
            Writer writer = new StringWriter();
            ObjectMapper mapper = getObjectMapper();
            ObjectWriter objectWriter = mapper.writerWithDefaultPrettyPrinter();
            JsonGenerator jsonGenerator = mapper.getFactory().createGenerator(writer);

            jsonGenerator.writeStartArray();
            objectWriter.forType(new TypeReference<Map<String, SensitivityFunction>>() { }).writeValue(jsonGenerator, sensitivityFunctionMap);
            objectWriter.forType(new TypeReference<Map<String, SensitivityVariable>>() { }).writeValue(jsonGenerator, sensitivityVariableMap);
            objectWriter.forType(new TypeReference<Set<String>>() { }).writeValue(jsonGenerator, basecaseSensitivityFunctions);
            objectWriter.forType(new TypeReference<Map<Contingency, Set<String>>>() { }).writeValue(jsonGenerator, sensitivityFunctionStringMap);
            jsonGenerator.writeEndArray();
            jsonGenerator.close();

            return writer.toString().getBytes("UTF-8");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static InternalSensitivityInputsProvider read(InputStream inputStream) {
        Objects.requireNonNull(inputStream);
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonParser jsonParser = mapper.getFactory().createParser(inputStream);

            JsonToken jsonToken;
            jsonToken = jsonParser.nextToken();
            jsonToken = jsonParser.nextToken();

            Map<String, SensitivityFunction> sensitivityFunctionMap = mapper
                .readValue(jsonParser, new TypeReference<LinkedHashMap<String, SensitivityFunction>>() { });

            jsonToken = jsonParser.nextToken();
            Map<String, SensitivityVariable> sensitivityVariableMap = mapper
                .readValue(jsonParser, new TypeReference<LinkedHashMap<String, SensitivityVariable>>() { });

            jsonToken = jsonParser.nextToken();
            Set<String> basecaseSensitivityFunctions = mapper
                .readValue(jsonParser, new TypeReference<>() { });
            List<SensitivityFactor> basecaseSensitivityFactors = new ArrayList<>();
            for (String sensitivityFunctionString : basecaseSensitivityFunctions) {
                SensitivityFunction function = sensitivityFunctionMap.get(sensitivityFunctionString);
                for (SensitivityVariable variable : sensitivityVariableMap.values()) {
                    basecaseSensitivityFactors.add(makeSensitivityFactor(function, variable));
                }
            }

            jsonToken = jsonParser.nextToken();
            Map<Contingency, Set<String>> sensitivityFunctionStringMap = mapper
                .readValue(jsonParser, new TypeReference<LinkedHashMap<Contingency, Set<String>>>() { });
            jsonParser.close();

            Map<String, List<SensitivityFactor>> sensitivityFactorsMap = new HashMap<>();
            for (Contingency contingency : sensitivityFunctionStringMap.keySet()) {
                String contingencyId = contingency.getId();
                List<SensitivityFactor> sensitivityFactors = new ArrayList<>();
                for (String sensitivityFunctionString : sensitivityFunctionStringMap.get(contingencyId)) {
                    SensitivityFunction function = sensitivityFunctionMap.get(sensitivityFunctionString);
                    for (SensitivityVariable variable : sensitivityVariableMap.values()) {
                        sensitivityFactors.add(makeSensitivityFactor(function, variable));
                    }
                }
                sensitivityFactorsMap.put(contingencyId, sensitivityFactors);
            }

            return new InternalSensitivityInputsProvider(new ArrayList<>(), basecaseSensitivityFactors, sensitivityFactorsMap, sensitivityFunctionStringMap.keySet().stream().collect(Collectors.toList()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SensitivityFactor makeSensitivityFactor(SensitivityFunction function, SensitivityVariable variable) {
        if (function instanceof BranchFlow) {
            if (variable instanceof LinearGlsk) {
                return new BranchFlowPerLinearGlsk((BranchFlow) function, (LinearGlsk) variable);
            } else if (variable instanceof PhaseTapChangerAngle) {
                return new BranchFlowPerPSTAngle((BranchFlow) function, (PhaseTapChangerAngle) variable);
            } else if (variable instanceof InjectionIncrease) {
                return new BranchFlowPerInjectionIncrease((BranchFlow) function, (InjectionIncrease) variable);
            }
        } else if (function instanceof BranchIntensity) {
            if (variable instanceof PhaseTapChangerAngle) {
                return new BranchIntensityPerPSTAngle((BranchIntensity) function, (PhaseTapChangerAngle) variable);
            }
        }
        throw new PowsyblException("Unable to parse JsonSensitivityFactorProvider: unrecognizable sensitivity factor");
    }

    private static ObjectMapper getObjectMapper() {
        return new ObjectMapper().registerModule(new ContingencyJsonModule());
    }
}
