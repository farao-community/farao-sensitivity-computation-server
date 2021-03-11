package com.farao_community.farao.sensitivity.api;

import com.powsybl.commons.PowsyblException;
import com.powsybl.sensitivity.SensitivityFunction;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;

public class JsonSensitivityUtil {
    private JsonSensitivityUtil() {
        throw new AssertionError("Utility class should not be implemented");
    }

    public static String getSuffix(SensitivityFunction function) {
        if (function instanceof BranchFlow) {
            return " - F";
        } else if (function instanceof BranchIntensity) {
            return " - I";
        }
        throw new PowsyblException("Unable to parse JsonSensitivityFactorProvider: unrecognizable sensitivity function");

    }
}
