/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Damon Brown
 */
package org.nuxeo.platform.appian.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppianResponse {

    @JsonProperty("pp")
    private Map<String, Object> pp = null;

    @JsonProperty("pm")
    private Map<String, Object> pm = null;

    @JsonProperty("pv")
    private Map<String, Object> pv = null;

    /**
     * 
     */
    public AppianResponse() {
        super();
    }

    public Map<String, Object> getProcessVariables() {
        return pv;
    }

    public Map<String, Object> getProcessProperties() {
        return pp;
    }

    public Map<String, Object> getProcessInstance() {
        return pm;
    }

    @Override
    public String toString() {
        return "AppianResponse [pp=" +
                pp +
                ", pm=" +
                pm +
                ", pv=" +
                pv +
                "]";
    }

}
