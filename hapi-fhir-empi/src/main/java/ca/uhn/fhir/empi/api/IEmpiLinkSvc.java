package ca.uhn.fhir.empi.api;

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

import org.hl7.fhir.instance.model.api.IBaseResource;

public interface IEmpiLinkSvc {

	/**
	 * Update a link between a Person record and its target Patient/Practitioner record. If a link does not exist between
	 * these two records, create it.
	 * @param thePerson the Person to link the target resource to.
	 * @param theResource the target resource, which is one of Patient/Practitioner
	 * @param theMatchResult the current status of the match to set the link to.
	 * @param theLinkSource the initiator of the change in link status.
	 */
	void updateLink(IBaseResource thePerson, IBaseResource theResource, EmpiMatchResultEnum theMatchResult, EmpiLinkSourceEnum theLinkSource);
}
