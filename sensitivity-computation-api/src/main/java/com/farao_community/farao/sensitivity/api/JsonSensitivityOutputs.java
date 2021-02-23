package com.farao_community.farao.sensitivity.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;

public class JsonSensitivityOutputs {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSensitivityOutputs.class);
    public static void write(SensitivityAnalysisResult sensitivityComputationResults, Writer writer) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonGenerator jsonGenerator = mapper.getFactory().createGenerator(writer);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectField("ok", sensitivityComputationResults.isOk());
            jsonGenerator.writeObjectField("metrics", sensitivityComputationResults.getMetrics());
            jsonGenerator.writeObjectField("logs", sensitivityComputationResults.getLogs());

            jsonGenerator.writeFieldName("values");
            jsonGenerator.writeStartArray();
            for (SensitivityValue sensitivityValue : sensitivityComputationResults.getSensitivityValues()) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("fun", sensitivityValue.getFactor().getFunction().getId());
                jsonGenerator.writeStringField("var", sensitivityValue.getFactor().getVariable().getId());
                jsonGenerator.writeNumberField("value", sensitivityValue.getValue());
                jsonGenerator.writeNumberField("funRef", sensitivityValue.getFunctionReference());
                jsonGenerator.writeNumberField("varRef", sensitivityValue.getVariableReference());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();

            Map<String, List<SensitivityValue>> coValMap = sensitivityComputationResults.getSensitivityValuesContingencies();
            jsonGenerator.writeFieldName("contingencyValues");
            jsonGenerator.writeStartObject();
            for(String coId : coValMap.keySet()) {
                jsonGenerator.writeFieldName(coId);
                jsonGenerator.writeStartArray();
                for (SensitivityValue sensitivityValue : coValMap.get(coId)) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("fun", sensitivityValue.getFactor().getFunction().getId());
                    jsonGenerator.writeStringField("var", sensitivityValue.getFactor().getVariable().getId());
                    jsonGenerator.writeNumberField("value", sensitivityValue.getValue());
                    jsonGenerator.writeNumberField("funRef", sensitivityValue.getFunctionReference());
                    jsonGenerator.writeNumberField("varRef", sensitivityValue.getVariableReference());
                    jsonGenerator.writeEndObject();
                }
                jsonGenerator.writeEndArray();
            }
            jsonGenerator.close();
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SensitivityAnalysisResult read(Reader reader, SensitivityFactorsProvider factorsProvider, Network network) throws IOException {
        LOGGER.debug("starting writing output");
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonParser jsonParser = mapper.getFactory().createParser(reader);

            JsonToken jsonToken;
            jsonToken = jsonParser.nextToken();

            jsonToken = jsonParser.nextToken();
            jsonToken = jsonParser.nextToken();
            boolean ok = mapper.readValue(jsonParser, Boolean.TYPE);

            jsonToken = jsonParser.nextToken();
            jsonToken = jsonParser.nextToken();
            Map<String, String> metrics = mapper.readValue(jsonParser, new TypeReference<>() {
            });

            jsonToken = jsonParser.nextToken();
            jsonToken = jsonParser.nextToken();
            String logs = mapper.readValue(jsonParser, new TypeReference<>() {
            });

            jsonToken = jsonParser.nextToken();
            jsonToken = jsonParser.nextToken();
            Set<Map<String, String>> baseCaseSensisSet  = mapper
                .readValue(jsonParser, new TypeReference<>() { });
            Map<String, Map<String, Map<String, Double>>> reorganizedSensis = new HashMap<>();
            for (Map<String, String> sensi : baseCaseSensisSet) {
                String fun = sensi.get("fun");
                String var = sensi.get("var");
                Map<String, Double> sensiRes = new HashMap<>();
                sensiRes.put("value", Double.parseDouble(sensi.get("value")));
                sensiRes.put("funRef", Double.parseDouble(sensi.get("funRef")));
                sensiRes.put("varRef", Double.parseDouble(sensi.get("varRef")));
                if (!reorganizedSensis.containsKey(fun)) {
                    reorganizedSensis.put(fun, new HashMap<>());
                }
                reorganizedSensis.get(fun).put(var, sensiRes);
            }
            List<SensitivityValue> baseCaseSensiValues = new LinkedList<>();
            List<SensitivityFactor> factors = factorsProvider.getAdditionalFactors(network);
            for(SensitivityFactor factor : factors) {
                Map<String, Double> sensiRes = reorganizedSensis.get(factor.getFunction().getId()).get(factor.getVariable().getId());
                baseCaseSensiValues.add(new SensitivityValue(factor, sensiRes.get("value"), sensiRes.get("funRef"), sensiRes.get("varRef")));
            }

            jsonToken = jsonParser.nextToken();
            jsonToken = jsonParser.nextToken();
            Map<String, Set<Map<String, String>>> allContingencySensisMap  = mapper
                .readValue(jsonParser, new TypeReference<>() { });
            Map<String, List<SensitivityValue>> allContingencySensiValues = new HashMap<>();
            for(String coId : allContingencySensisMap.keySet()) {
                reorganizedSensis = new HashMap<>();
                Set<Map<String, String>> contingencySensisSet = allContingencySensisMap.get(coId);
                for (Map<String, String> sensi : contingencySensisSet) {
                    String fun = sensi.get("fun");
                    String var = sensi.get("var");
                    Map<String, Double> sensiRes = new HashMap<>();
                    sensiRes.put("value", Double.parseDouble(sensi.get("value")));
                    sensiRes.put("funRef", Double.parseDouble(sensi.get("funRef")));
                    sensiRes.put("varRef", Double.parseDouble(sensi.get("varRef")));
                    if (!reorganizedSensis.containsKey(fun)) {
                        reorganizedSensis.put(fun, new HashMap<>());
                    }
                    reorganizedSensis.get(fun).put(var, sensiRes);
                }
                List<SensitivityValue> contingencySensiValues = new LinkedList<>();
                factors = factorsProvider.getAdditionalFactors(network, coId);
                for(SensitivityFactor factor : factors) {
                    Map<String, Double> sensiRes = reorganizedSensis.get(factor.getFunction().getId()).get(factor.getVariable().getId());
                    contingencySensiValues.add(new SensitivityValue(factor, sensiRes.get("value"), sensiRes.get("funRef"), sensiRes.get("varRef")));
                }
                allContingencySensiValues.put(coId, contingencySensiValues);
            }
            reader.close();
            jsonParser.close();
            LOGGER.debug("ouput written");
            return new SensitivityAnalysisResult(ok, metrics, logs, baseCaseSensiValues, allContingencySensiValues);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
