package ca.uhn.fhir.jpa.provider;

/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2021 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.api.model.ExpungeOptions;
import ca.uhn.fhir.jpa.api.model.ExpungeOutcome;
import ca.uhn.fhir.jpa.model.util.JpaConstants;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.At;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Patch;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Since;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
import ca.uhn.fhir.rest.api.ValidationModeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.CoverageIgnore;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Required;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

import static ca.uhn.fhir.jpa.model.util.JpaConstants.OPERATION_META;
import static ca.uhn.fhir.jpa.model.util.JpaConstants.OPERATION_META_ADD;
import static ca.uhn.fhir.jpa.model.util.JpaConstants.OPERATION_META_DELETE;

public abstract class BaseJpaResourceProvider<T extends IBaseResource> extends BaseJpaProvider implements IResourceProvider {

	private IFhirResourceDao<T> myDao;

	public BaseJpaResourceProvider() {
		// nothing
	}

	@CoverageIgnore
	public BaseJpaResourceProvider(IFhirResourceDao<T> theDao) {
		myDao = theDao;
	}


	protected Parameters doExpunge(IIdType theIdParam, IPrimitiveType<? extends Integer> theLimit, IPrimitiveType<? extends Boolean> theExpungeDeletedResources, IPrimitiveType<? extends Boolean> theExpungeOldVersions, IPrimitiveType<? extends Boolean> theExpungeEverything, RequestDetails theRequest) {

		ExpungeOptions options = createExpungeOptions(theLimit, theExpungeDeletedResources, theExpungeOldVersions, theExpungeEverything);

		ExpungeOutcome outcome;
		if (theIdParam != null) {
			outcome = getDao().expunge(theIdParam, options, theRequest);
		} else {
			outcome = getDao().expunge(options, theRequest);
		}

		return createExpungeResponse(outcome);
	}


	public IFhirResourceDao<T> getDao() {
		return myDao;
	}

	@Required
	public void setDao(IFhirResourceDao<T> theDao) {
		myDao = theDao;
	}

	@History
	public IBundleProvider getHistoryForResourceInstance(
		HttpServletRequest theRequest,
		@IdParam IIdType theId,
		@Since Date theSince,
		@At DateRangeParam theAt,
		RequestDetails theRequestDetails) {

		startRequest(theRequest);
		try {
			DateRangeParam sinceOrAt = processSinceOrAt(theSince, theAt);
			return myDao.history(theId, sinceOrAt.getLowerBoundAsInstant(), sinceOrAt.getUpperBoundAsInstant(), theRequestDetails);
		} finally {
			endRequest(theRequest);
		}
	}

	@History
	public IBundleProvider getHistoryForResourceType(
		HttpServletRequest theRequest,
		@Since Date theSince,
		@At DateRangeParam theAt,
		RequestDetails theRequestDetails) {
		startRequest(theRequest);
		try {
			DateRangeParam sinceOrAt = processSinceOrAt(theSince, theAt);
			return myDao.history(sinceOrAt.getLowerBoundAsInstant(), sinceOrAt.getUpperBoundAsInstant(), theRequestDetails);
		} finally {
			endRequest(theRequest);
		}
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return myDao.getResourceType();
	}

	@Patch
	public DaoMethodOutcome patch(HttpServletRequest theRequest, @IdParam IIdType theId, @ConditionalUrlParam String theConditionalUrl, RequestDetails theRequestDetails, @ResourceParam String theBody, PatchTypeEnum thePatchType, @ResourceParam IBaseParameters theRequestBody) {
		startRequest(theRequest);
		try {
			return myDao.patch(theId, theConditionalUrl, thePatchType, theBody, theRequestBody, theRequestDetails);
		} finally {
			endRequest(theRequest);
		}
	}

	@Read(version = true)
	public T read(HttpServletRequest theRequest, @IdParam IIdType theId, RequestDetails theRequestDetails) {
		startRequest(theRequest);
		try {
			return myDao.read(theId, theRequestDetails);
		} finally {
			endRequest(theRequest);
		}
	}

	@Create
	public MethodOutcome create(HttpServletRequest theRequest, @ResourceParam T theResource, @ConditionalUrlParam String theConditional, RequestDetails theRequestDetails) {
		startRequest(theRequest);
		try {
			if (theConditional != null) {
				return getDao().create(theResource, theConditional, theRequestDetails);
			} else {
				return getDao().create(theResource, theRequestDetails);
			}
		} finally {
			endRequest(theRequest);
		}
	}

	@Delete()
	public MethodOutcome delete(HttpServletRequest theRequest, @IdParam IIdType theResource, @ConditionalUrlParam(supportsMultiple = true) String theConditional, RequestDetails theRequestDetails) {
		startRequest(theRequest);
		try {
			if (theConditional != null) {
				return getDao().deleteByUrl(theConditional, theRequestDetails);
			} else {
				return getDao().delete(theResource, theRequestDetails);
			}
		} finally {
			endRequest(theRequest);
		}
	}

	@Operation(name = JpaConstants.OPERATION_EXPUNGE, idempotent = false, returnParameters = {
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_OUT_PARAM_EXPUNGE_COUNT, typeName = "integer")
	})
	public Parameters expunge(
		@IdParam IIdType theIdParam,
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_PARAM_LIMIT, typeName = "integer") IPrimitiveType<Integer> theLimit,
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_PARAM_EXPUNGE_DELETED_RESOURCES, typeName = "boolean") IPrimitiveType<Boolean> theExpungeDeletedResources,
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_PARAM_EXPUNGE_PREVIOUS_VERSIONS, typeName = "boolean") IPrimitiveType<Boolean> theExpungeOldVersions,
		RequestDetails theRequest) {
		return doExpunge(theIdParam, theLimit, theExpungeDeletedResources, theExpungeOldVersions, null, theRequest);
	}

	@Operation(name = JpaConstants.OPERATION_EXPUNGE, idempotent = false, returnParameters = {
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_OUT_PARAM_EXPUNGE_COUNT, typeName = "integer")
	})
	public Parameters expunge(
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_PARAM_LIMIT, typeName = "integer") IPrimitiveType<Integer> theLimit,
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_PARAM_EXPUNGE_DELETED_RESOURCES, typeName = "boolean") IPrimitiveType<Boolean> theExpungeDeletedResources,
		@OperationParam(name = JpaConstants.OPERATION_EXPUNGE_PARAM_EXPUNGE_PREVIOUS_VERSIONS, typeName = "boolean") IPrimitiveType<Boolean> theExpungeOldVersions,
		RequestDetails theRequest) {
		return doExpunge(null, theLimit, theExpungeDeletedResources, theExpungeOldVersions, null, theRequest);
	}

	@Description("Request a global list of tags, profiles, and security labels")
	@Operation(name = OPERATION_META, idempotent = true, returnParameters = {
		@OperationParam(name = "return", typeName = "Meta")
	})
	public Parameters meta(RequestDetails theRequestDetails) {
		Parameters parameters = new Parameters();
		Meta metaGetOperation = getDao().metaGetOperation(Meta.class, theRequestDetails);
		parameters.addParameter().setName("return").setValue(metaGetOperation);
		return parameters;
	}

	@Description("Request a list of tags, profiles, and security labels for a specfic resource instance")
	@Operation(name = OPERATION_META, idempotent = true, returnParameters = {
		@OperationParam(name = "return", typeName = "Meta")
	})
	public Parameters meta(@IdParam IIdType theId, RequestDetails theRequestDetails) {
		Parameters parameters = new Parameters();
		Meta metaGetOperation = getDao().metaGetOperation(Meta.class, theId, theRequestDetails);
		parameters.addParameter().setName("return").setValue(metaGetOperation);
		return parameters;
	}

	@Description("Add tags, profiles, and/or security labels to a resource")
	@Operation(name = OPERATION_META_ADD, idempotent = false, returnParameters = {
		@OperationParam(name = "return", typeName = "Meta")
	})
	public Parameters metaAdd(@IdParam IIdType theId, @OperationParam(name = "meta") Meta theMeta, RequestDetails theRequestDetails) {
		if (theMeta == null) {
			throw new InvalidRequestException("Input contains no parameter with name 'meta'");
		}
		Parameters parameters = new Parameters();
		Meta metaAddOperation = getDao().metaAddOperation(theId, theMeta, theRequestDetails);
		parameters.addParameter().setName("return").setValue(metaAddOperation);
		return parameters;
	}

	@Description("Delete tags, profiles, and/or security labels from a resource")
	@Operation(name = OPERATION_META_DELETE, idempotent = false, returnParameters = {
		@OperationParam(name = "return", typeName = "Meta")
	})
	public Parameters metaDelete(@IdParam IIdType theId, @OperationParam(name = "meta") Meta theMeta, RequestDetails theRequestDetails) {
		if (theMeta == null) {
			throw new InvalidRequestException("Input contains no parameter with name 'meta'");
		}
		Parameters parameters = new Parameters();
		parameters.addParameter().setName("return").setValue(getDao().metaDeleteOperation(theId, theMeta, theRequestDetails));
		return parameters;
	}

	@Update
	public MethodOutcome update(HttpServletRequest theRequest, @ResourceParam T theResource, @IdParam IIdType theId, @ConditionalUrlParam String theConditional, RequestDetails theRequestDetails) {
		startRequest(theRequest);
		try {
			if (theConditional != null) {
				return getDao().update(theResource, theConditional, theRequestDetails);
			} else {
				return getDao().update(theResource, theRequestDetails);
			}
		} finally {
			endRequest(theRequest);
		}
	}

	@Validate
	public MethodOutcome validate(@ResourceParam T theResource, @ResourceParam String theRawResource, @ResourceParam EncodingEnum theEncoding, @Validate.Mode ValidationModeEnum theMode,
											@Validate.Profile String theProfile, RequestDetails theRequestDetails) {
		return validate(theResource, null, theRawResource, theEncoding, theMode, theProfile, theRequestDetails);
	}

	@Validate
	public MethodOutcome validate(@ResourceParam T theResource, @IdParam IIdType theId, @ResourceParam String theRawResource, @ResourceParam EncodingEnum theEncoding, @Validate.Mode ValidationModeEnum theMode,
											@Validate.Profile String theProfile, RequestDetails theRequestDetails) {
		return getDao().validate(theResource, theId, theRawResource, theEncoding, theMode, theProfile, theRequestDetails);
	}

}
