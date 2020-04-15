package ca.uhn.fhir.jpa.empi.matcher;

import ca.uhn.fhir.empi.api.EmpiMatchResultEnum;
import ca.uhn.fhir.jpa.empi.svc.EmpiLinkDaoSvc;
import ca.uhn.fhir.jpa.empi.svc.ResourceTableHelper;
import ca.uhn.fhir.jpa.entity.EmpiLink;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Matcher with tells us if there is an EmpiLink with between these two resources that are considered POSSIBLE_MATCH
 */
public class IsPossibleMatchWith extends BasePersonMatcher {

	protected IsPossibleMatchWith(ResourceTableHelper theResourceTableHelper, EmpiLinkDaoSvc theEmpiLinkDaoSvc, IBaseResource... theBaseResource) {
		super(theResourceTableHelper, theEmpiLinkDaoSvc, theBaseResource);
	}

	@Override
	protected boolean matchesSafely(IBaseResource theIncomingResource) {
		List<EmpiLink> empiLinks = getEmpiLinks(theIncomingResource, EmpiMatchResultEnum.POSSIBLE_MATCH);

		List<Long> personPidsToMatch = myBaseResources.stream()
			.map(iBaseResource -> getMatchedPersonPidFromResource(iBaseResource))
			.collect(Collectors.toList());

		List<Long> empiLinkSourcePersonPids = empiLinks.stream().map(EmpiLink::getPersonPid).collect(Collectors.toList());

		return empiLinkSourcePersonPids.containsAll(personPidsToMatch);
	}

	@Override
	public void describeTo(Description theDescription) {
		theDescription.appendText(" no link found with POSSIBLE_MATCH to the requested PIDS");
	}

	@Override
	protected void describeMismatchSafely(IBaseResource item, Description mismatchDescription) {
		super.describeMismatchSafely(item, mismatchDescription);
		mismatchDescription.appendText("No Empi Link With POSSIBLE_MATCH was found");
	}

	public static Matcher<IBaseResource> possibleMatchWith(ResourceTableHelper theResourceTableHelper, EmpiLinkDaoSvc theEmpiLinkDaoSvc, IBaseResource... theBaseResource) {
		return new IsPossibleMatchWith(theResourceTableHelper, theEmpiLinkDaoSvc, theBaseResource);
	}
}
