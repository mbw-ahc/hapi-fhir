package ca.uhn.fhir.empi.rules.config;

/*-
 * #%L
 * HAPI FHIR - Enterprise Master Patient Index
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.empi.api.IEmpiProperties;
import ca.uhn.fhir.empi.rules.json.EmpiRulesJson;
import ca.uhn.fhir.util.JsonUtil;

import java.io.IOException;

public class EmpiPropertiesImpl implements IEmpiProperties {
	private boolean myEnabled;
	private int myConcurrentConsumers = EMPI_DEFAULT_CONCURRENT_CONSUMERS;
	private String myScriptText;
	private EmpiRulesJson myEmpiRules;

	@Override
	public boolean isEnabled() {
		return myEnabled;
	}

	public EmpiPropertiesImpl setEnabled(boolean theEnabled) {
		myEnabled = theEnabled;
		return this;
	}

	@Override
	public int getConcurrentConsumers() {
		return myConcurrentConsumers;
	}

	public EmpiPropertiesImpl setConcurrentConsumers(int theConcurrentConsumers) {
		myConcurrentConsumers = theConcurrentConsumers;
		return this;
	}

	public String getScriptText() {
		return myScriptText;
	}

	public EmpiPropertiesImpl setScriptText(String theScriptText) throws IOException {
		myScriptText = theScriptText;
		myEmpiRules = JsonUtil.deserialize(theScriptText, EmpiRulesJson.class);
		return this;
	}

	@Override
	public EmpiRulesJson getEmpiRules() {
		return myEmpiRules;
	}

	public EmpiPropertiesImpl setEmpiRules(EmpiRulesJson theEmpiRules) {
		myEmpiRules = theEmpiRules;
		return this;
	}
}
