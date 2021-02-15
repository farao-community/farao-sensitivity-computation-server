/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity.server;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey@rte-france.com>}
 */
@RestController
@RequestMapping("/api/v1/sensitivity-computation")
public class SensitivityComputationServerController {
    private final SensitivityComputationServerService service;

    public SensitivityComputationServerController(SensitivityComputationServerService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<Flux<DataBuffer>>> runComputation(FilePart networkFile,
                                                                 FilePart inputsFile,
                                                                 FilePart parametersFile) {
        return Mono.just(ResponseEntity.ok(service.runComputation(networkFile, inputsFile, parametersFile)));
    }
}
