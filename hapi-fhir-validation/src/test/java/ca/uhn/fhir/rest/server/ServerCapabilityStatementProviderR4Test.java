package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.method.BaseMethodBinding;
import ca.uhn.fhir.rest.server.method.IParameter;
import ca.uhn.fhir.rest.server.method.SearchMethodBinding;
import ca.uhn.fhir.rest.server.method.SearchParameter;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.google.common.collect.Lists;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.ConditionalDeleteStatus;
import org.hl7.fhir.r4.model.CapabilityStatement.SystemRestfulInteraction;
import org.hl7.fhir.r4.model.CapabilityStatement.TypeRestfulInteraction;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationDefinition;
import org.hl7.fhir.r4.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r4.model.OperationDefinition.OperationKind;
import org.hl7.fhir.r4.model.OperationDefinition.OperationParameterUse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerCapabilityStatementProviderR4Test {

    public static final String PATIENT_SUB = "PatientSub";
    public static final String PATIENT_SUB_SUB = "PatientSubSub";
    public static final String PATIENT_SUB_SUB_2 = "PatientSubSub2";
    public static final String PATIENT_TRIPLE_SUB = "PatientTripleSub";
    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ServerCapabilityStatementProviderR4Test.class);
    private final FhirContext myCtx = FhirContext.forCached(FhirVersionEnum.R4);
    private FhirValidator myValidator;

    @BeforeEach
    public void before() {
        myValidator = myCtx.newValidator();
        myValidator.registerValidatorModule(new FhirInstanceValidator(myCtx));
    }

    private HttpServletRequest createHttpServletRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/FhirStorm/fhir/Patient/_search");
        when(req.getServletPath()).thenReturn("/fhir");
        when(req.getRequestURL()).thenReturn(new StringBuffer().append("http://fhirstorm.dyndns.org:8080/FhirStorm/fhir/Patient/_search"));
        when(req.getContextPath()).thenReturn("/FhirStorm");
        return req;
    }

    private ServletConfig createServletConfig() {
        ServletConfig sc = mock(ServletConfig.class);
        when(sc.getServletContext()).thenReturn(null);
        return sc;
    }

    private CapabilityStatementRestResourceComponent findRestResource(CapabilityStatement conformance, String wantResource) throws Exception {
        CapabilityStatementRestResourceComponent resource = null;
        for (CapabilityStatementRestResourceComponent next : conformance.getRest().get(0).getResource()) {
            if (next.getType().equals(wantResource)) {
                resource = next;
            }
        }
        if (resource == null) {
            throw new Exception("Could not find resource: " + wantResource);
        }
        return resource;
    }

    @Test
    public void testFormats() throws ServletException {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new ConditionalProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement cs = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        List<String> formats = cs
                .getFormat()
                .stream()
                .map(t -> t.getCode())
                .collect(Collectors.toList());
        assertThat(formats.toString(), formats, containsInAnyOrder(
                "application/fhir+xml",
                "xml",
                "application/fhir+json",
                "json",
                "application/x-turtle",
                "ttl"
        ));
    }


    @Test
    public void testConditionalOperations() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new ConditionalProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
        ourLog.info(conf);

        assertEquals(2, conformance.getRest().get(0).getResource().size());
        CapabilityStatementRestResourceComponent res = conformance.getRest().get(0).getResource().get(1);
        assertEquals("Patient", res.getType());

        assertTrue(res.getConditionalCreate());
        assertEquals(ConditionalDeleteStatus.MULTIPLE, res.getConditionalDelete());
        assertTrue(res.getConditionalUpdate());
    }

    private RequestDetails createRequestDetails(RestfulServer theServer) {
        ServletRequestDetails retVal = new ServletRequestDetails(null);
        retVal.setServer(theServer);
        retVal.setFhirServerBase("http://localhost/baseR4");
        return retVal;
    }

    @Test
    public void testExtendedOperationReturningBundle() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new ProviderWithExtendedOperationReturningBundle());
        rs.setServerAddressStrategy(new HardcodedServerAddressStrategy("http://localhost/baseR4"));

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        validate(conformance);

        assertEquals(1, conformance.getRest().get(0).getOperation().size());
        assertEquals("everything", conformance.getRest().get(0).getOperation().get(0).getName());

        OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-everything"), createRequestDetails(rs));
        validate(opDef);
        assertEquals("everything", opDef.getCode());
        assertThat(opDef.getSystem(), is(false));
        assertThat(opDef.getType(), is(false));
        assertThat(opDef.getInstance(), is(true));
    }

    @Test
    public void testExtendedOperationReturningBundleOperation() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new ProviderWithExtendedOperationReturningBundle());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs) {
        };
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-everything"), createRequestDetails(rs));
        validate(opDef);

        String conf = myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef);
        ourLog.info(conf);

        assertEquals("everything", opDef.getCode());
        assertEquals(false, opDef.getAffectsState());
    }

    @Test
    public void testInstanceHistorySupported() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new InstanceHistoryProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = validate(conformance);

        conf = myCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
        assertThat(conf, containsString("<interaction><code value=\"" + TypeRestfulInteraction.HISTORYINSTANCE.toCode() + "\"/></interaction>"));
    }

    @Test
    public void testMultiOptionalDocumentation() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new MultiOptionalProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        boolean found = false;
        Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
        for (ResourceBinding resourceBinding : resourceBindings) {
            if (resourceBinding.getResourceName().equals("Patient")) {
                List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
                SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
                SearchParameter param = (SearchParameter) binding.getParameters().iterator().next();
                assertEquals("The patient's identifier", param.getDescription());
                found = true;
            }
        }

        assertTrue(found);
        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = validate(conformance);

        assertThat(conf, containsString("<documentation value=\"The patient's identifier\"/>"));
        assertThat(conf, containsString("<documentation value=\"The patient's name\"/>"));
        assertThat(conf, containsString("<type value=\"token\"/>"));
    }

    @Test
    public void testNonConditionalOperations() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new NonConditionalProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        validate(conformance);

        CapabilityStatementRestResourceComponent res = conformance.getRest().get(0).getResource().get(1);
        assertEquals("Patient", res.getType());

        assertNull(res.getConditionalCreateElement().getValue());
        assertNull(res.getConditionalDeleteElement().getValue());
        assertNull(res.getConditionalUpdateElement().getValue());
    }

    /**
     * See #379
     */
    @Test
    public void testOperationAcrossMultipleTypes() throws Exception {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new MultiTypePatientProvider(), new MultiTypeEncounterProvider());
        rs.setServerAddressStrategy(new HardcodedServerAddressStrategy("http://localhost/baseR4"));

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        validate(conformance);

        assertEquals(4, conformance.getRest().get(0).getOperation().size());
        List<String> operationNames = toOperationNames(conformance.getRest().get(0).getOperation());
        assertThat(operationNames, containsInAnyOrder("someOp", "validate", "someOp", "validate"));

        List<String> operationIdParts = toOperationIdParts(conformance.getRest().get(0).getOperation());
        assertThat(operationIdParts, containsInAnyOrder("Patient-i-someOp", "Encounter-i-someOp", "Patient-i-validate", "Encounter-i-validate"));

        {
            OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-someOp"), createRequestDetails(rs));
            validate(opDef);
            ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef));
            Set<String> types = toStrings(opDef.getResource());
            assertEquals("someOp", opDef.getCode());
            assertEquals(true, opDef.getInstance());
            assertEquals(false, opDef.getSystem());
            assertThat(types, containsInAnyOrder("Patient"));
            assertEquals(2, opDef.getParameter().size());
            assertEquals("someOpParam1", opDef.getParameter().get(0).getName());
            assertEquals("date", opDef.getParameter().get(0).getType());
            assertEquals("someOpParam2", opDef.getParameter().get(1).getName());
            assertEquals("Patient", opDef.getParameter().get(1).getType());
        }
        {
            OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType("OperationDefinition/Encounter-i-someOp"), createRequestDetails(rs));
            validate(opDef);
            ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef));
            Set<String> types = toStrings(opDef.getResource());
            assertEquals("someOp", opDef.getCode());
            assertEquals(true, opDef.getInstance());
            assertEquals(false, opDef.getSystem());
            assertThat(types, containsInAnyOrder("Encounter"));
            assertEquals(2, opDef.getParameter().size());
            assertEquals("someOpParam1", opDef.getParameter().get(0).getName());
            assertEquals("date", opDef.getParameter().get(0).getType());
            assertEquals("someOpParam2", opDef.getParameter().get(1).getName());
            assertEquals("Encounter", opDef.getParameter().get(1).getType());
        }
        {
            OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-validate"), createRequestDetails(rs));
            validate(opDef);
            ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef));
            Set<String> types = toStrings(opDef.getResource());
            assertEquals("validate", opDef.getCode());
            assertEquals(true, opDef.getInstance());
            assertEquals(false, opDef.getSystem());
            assertThat(types, containsInAnyOrder("Patient"));
            assertEquals(1, opDef.getParameter().size());
            assertEquals("resource", opDef.getParameter().get(0).getName());
            assertEquals("Patient", opDef.getParameter().get(0).getType());
        }
    }

    @Test
    public void testOperationDocumentation() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new SearchProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        String conf = validate(conformance);

        assertThat(conf, containsString("<documentation value=\"The patient's identifier (MRN or other card number)\"/>"));
        assertThat(conf, containsString("<type value=\"token\"/>"));

    }

    @Test
    public void testOperationOnNoTypes() throws Exception {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new PlainProviderWithExtendedOperationOnNoType());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs) {
            @Override
            public CapabilityStatement getServerConformance(HttpServletRequest theRequest, RequestDetails theRequestDetails) {
                return (CapabilityStatement) super.getServerConformance(theRequest, createRequestDetails(rs));
            }
        };
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType("OperationDefinition/-is-plain"), createRequestDetails(rs));
        validate(opDef);

        assertEquals("plain", opDef.getCode());
        assertEquals(false, opDef.getAffectsState());
        assertEquals(3, opDef.getParameter().size());

        assertTrue(opDef.getParameter().get(0).hasName());
        assertEquals("start", opDef.getParameter().get(0).getName());
        assertEquals("in", opDef.getParameter().get(0).getUse().toCode());
        assertEquals("0", opDef.getParameter().get(0).getMinElement().getValueAsString());
        assertEquals("date", opDef.getParameter().get(0).getTypeElement().getValueAsString());

        assertEquals("out1", opDef.getParameter().get(2).getName());
        assertEquals("out", opDef.getParameter().get(2).getUse().toCode());
        assertEquals("1", opDef.getParameter().get(2).getMinElement().getValueAsString());
        assertEquals("2", opDef.getParameter().get(2).getMaxElement().getValueAsString());
        assertEquals("string", opDef.getParameter().get(2).getTypeElement().getValueAsString());

        assertThat(opDef.getSystem(), is(true));
        assertThat(opDef.getType(), is(false));
        assertThat(opDef.getInstance(), is(true));
    }

    @Test
    public void testProviderWithRequiredAndOptional() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new ProviderWithRequiredAndOptional());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        validate(conformance);

        CapabilityStatementRestComponent rest = conformance.getRest().get(0);
        CapabilityStatementRestResourceComponent res = rest.getResource().get(0);
        assertEquals("DiagnosticReport", res.getType());

        assertEquals("subject.identifier", res.getSearchParam().get(0).getName());
//		assertEquals("identifier", res.getSearchParam().get(0).getChain().get(0).getValue());

        assertEquals(DiagnosticReport.SP_CODE, res.getSearchParam().get(1).getName());

        assertEquals(DiagnosticReport.SP_DATE, res.getSearchParam().get(2).getName());

        assertEquals(1, res.getSearchInclude().size());
        assertEquals("DiagnosticReport.result", res.getSearchInclude().get(0).getValue());
    }

    @Test
    public void testReadAndVReadSupported() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new VreadProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = validate(conformance);

        assertThat(conf, containsString("<interaction><code value=\"vread\"/></interaction>"));
        assertThat(conf, containsString("<interaction><code value=\"read\"/></interaction>"));
    }

    @Test
    public void testReadSupported() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new ReadProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
        ourLog.info(conf);

        conf = myCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
        assertThat(conf, not(containsString("<interaction><code value=\"vread\"/></interaction>")));
        assertThat(conf, containsString("<interaction><code value=\"read\"/></interaction>"));
    }

    @Test
    public void testSearchParameterDocumentation() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new SearchProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        boolean found = false;
        Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
        for (ResourceBinding resourceBinding : resourceBindings) {
            if (resourceBinding.getResourceName().equals("Patient")) {
                List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
                SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
                for (IParameter next : binding.getParameters()) {
                    SearchParameter param = (SearchParameter) next;
                    if (param.getDescription().contains("The patient's identifier (MRN or other card number")) {
                        found = true;
                    }
                }
                found = true;
            }
        }
        assertTrue(found);
        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        String conf = validate(conformance);

        assertThat(conf, containsString("<documentation value=\"The patient's identifier (MRN or other card number)\"/>"));
        assertThat(conf, containsString("<type value=\"token\"/>"));

    }


    @Test
    public void testFormatIncludesSpecialNonMediaTypeFormats() throws ServletException {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new SearchProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());
        CapabilityStatement serverConformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        List<String> formatCodes = serverConformance.getFormat().stream().map(c -> c.getCode()).collect(Collectors.toList());

        assertThat(formatCodes, hasItem(Constants.FORMAT_XML));
        assertThat(formatCodes, hasItem(Constants.FORMAT_JSON));
        assertThat(formatCodes, hasItem(Constants.CT_FHIR_JSON_NEW));
        assertThat(formatCodes, hasItem(Constants.CT_FHIR_XML_NEW));
    }

    /**
     * See #286
     */
    @Test
    public void testSearchReferenceParameterDocumentation() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new PatientResourceProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        boolean found = false;
        Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
        for (ResourceBinding resourceBinding : resourceBindings) {
            if (resourceBinding.getResourceName().equals("Patient")) {
                List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
                SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
                SearchParameter param = (SearchParameter) binding.getParameters().get(25);
                assertEquals("The organization at which this person is a patient", param.getDescription());
                found = true;
            }
        }
        assertTrue(found);
        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        String conf = validate(conformance);

    }

    /**
     * See #286
     */
    @Test
    public void testSearchReferenceParameterWithWhitelistDocumentation() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new SearchProviderWithWhitelist());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        boolean found = false;
        Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
        for (ResourceBinding resourceBinding : resourceBindings) {
            if (resourceBinding.getResourceName().equals("Patient")) {
                List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
                SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
                SearchParameter param = (SearchParameter) binding.getParameters().get(0);
                assertEquals("The organization at which this person is a patient", param.getDescription());
                found = true;
            }
        }
        assertTrue(found);
        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        String conf = validate(conformance);

        CapabilityStatementRestResourceComponent resource = findRestResource(conformance, "Patient");

        CapabilityStatementRestResourceSearchParamComponent param = resource.getSearchParam().get(0);
//		assertEquals("bar", param.getChain().get(0).getValue());
//		assertEquals("foo", param.getChain().get(1).getValue());
//		assertEquals(2, param.getChain().size());
    }

    @Test
    public void testSearchReferenceParameterWithList() throws Exception {

        RestfulServer rsNoType = new RestfulServer(myCtx) {
            @Override
            public RestfulServerConfiguration createConfiguration() {
                RestfulServerConfiguration retVal = super.createConfiguration();
                retVal.setConformanceDate(new InstantDt("2011-02-22T11:22:33Z"));
                return retVal;
            }
        };
        rsNoType.registerProvider(new SearchProviderWithListNoType());
        ServerCapabilityStatementProvider scNoType = new ServerCapabilityStatementProvider(rsNoType);
        rsNoType.setServerConformanceProvider(scNoType);
        rsNoType.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) scNoType.getServerConformance(createHttpServletRequest(), createRequestDetails(rsNoType));
        conformance.setId("");
        String confNoType = validate(conformance);

        RestfulServer rsWithType = new RestfulServer(myCtx) {
            @Override
            public RestfulServerConfiguration createConfiguration() {
                RestfulServerConfiguration retVal = super.createConfiguration();
                retVal.setConformanceDate(new InstantDt("2011-02-22T11:22:33Z"));
                return retVal;
            }
        };
        rsWithType.registerProvider(new SearchProviderWithListWithType());
        ServerCapabilityStatementProvider scWithType = new ServerCapabilityStatementProvider(rsWithType);
        rsWithType.setServerConformanceProvider(scWithType);
        rsWithType.init(createServletConfig());

        CapabilityStatement conformanceWithType = (CapabilityStatement) scWithType.getServerConformance(createHttpServletRequest(), createRequestDetails(rsWithType));
        conformanceWithType.setId("");
        String confWithType = validate(conformanceWithType);

        assertEquals(confNoType, confWithType);
        assertThat(confNoType, containsString("<date value=\"2011-02-22T11:22:33Z\"/>"));
    }

    @Test
    public void testSystemHistorySupported() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new SystemHistoryProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = validate(conformance);

        assertThat(conf, containsString("<interaction><code value=\"" + SystemRestfulInteraction.HISTORYSYSTEM.toCode() + "\"/></interaction>"));
    }

    @Test
    public void testTypeHistorySupported() throws Exception {

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new TypeHistoryProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        String conf = validate(conformance);

        assertThat(conf, containsString("<interaction><code value=\"" + TypeRestfulInteraction.HISTORYTYPE.toCode() + "\"/></interaction>"));
    }

    @Test
    public void testStaticIncludeChains() throws Exception {

        class MyProvider implements IResourceProvider {

            @Override
            public Class<DiagnosticReport> getResourceType() {
                return DiagnosticReport.class;
            }

            @Search
            public List<IBaseResource> search(@RequiredParam(name = DiagnosticReport.SP_PATIENT + "." + Patient.SP_FAMILY) StringParam lastName,
                                              @RequiredParam(name = DiagnosticReport.SP_PATIENT + "." + Patient.SP_GIVEN) StringParam firstName,
                                              @RequiredParam(name = DiagnosticReport.SP_PATIENT + "." + Patient.SP_BIRTHDATE) DateParam dob,
                                              @OptionalParam(name = DiagnosticReport.SP_DATE) DateRangeParam range) {
                return null;
            }

        }

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new MyProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs) {
        };
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement opDef = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        validate(opDef);

        CapabilityStatementRestResourceComponent resource = opDef.getRest().get(0).getResource().get(0);
        assertEquals("DiagnosticReport", resource.getType());
        List<String> searchParamNames = resource.getSearchParam().stream().map(t -> t.getName()).collect(Collectors.toList());
        assertThat(searchParamNames, containsInAnyOrder("patient.birthdate", "patient.family", "patient.given", "date"));
    }

    @Test
    public void testIncludeLastUpdatedSearchParam() throws Exception {

        class MyProvider implements IResourceProvider {

            @Override
            public Class<DiagnosticReport> getResourceType() {
                return DiagnosticReport.class;
            }

            @Search
            public List<IBaseResource> search(@OptionalParam(name = DiagnosticReport.SP_DATE)
                                                      DateRangeParam range,

                                              @Description(shortDefinition = "Only return resources which were last updated as specified by the given range")
                                              @OptionalParam(name = "_lastUpdated")
                                                      DateRangeParam theLastUpdated
            ) {
                return null;
            }

        }

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new MyProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs) {
        };
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement opDef = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        validate(opDef);

        CapabilityStatementRestResourceComponent resource = opDef.getRest().get(0).getResource().get(0);
        assertEquals("DiagnosticReport", resource.getType());
        List<String> searchParamNames = resource.getSearchParam().stream().map(t -> t.getName()).collect(Collectors.toList());
        assertThat(searchParamNames, containsInAnyOrder("date", "_lastUpdated"));
    }

    @Test
    public void testSystemLevelNamedQueryWithParameters() throws Exception {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new NamedQueryPlainProvider());
        rs.setServerAddressStrategy(new HardcodedServerAddressStrategy("http://localhost/baseR4"));

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        validate(conformance);

        CapabilityStatementRestComponent restComponent = conformance.getRest().get(0);
        CapabilityStatementRestResourceOperationComponent operationComponent = restComponent.getOperation().get(0);
        assertThat(operationComponent.getName(), is(NamedQueryPlainProvider.QUERY_NAME));

        String operationReference = operationComponent.getDefinition();
        assertThat(operationReference, not(nullValue()));

        OperationDefinition operationDefinition = (OperationDefinition) sc.readOperationDefinition(new IdType(operationReference), createRequestDetails(rs));
        ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationDefinition));
        validate(operationDefinition);
        assertThat(operationDefinition.getCode(), is(NamedQueryPlainProvider.QUERY_NAME));
        assertThat(operationDefinition.getName(), is("Search_" + NamedQueryPlainProvider.QUERY_NAME));
        assertThat(operationDefinition.getStatus(), is(PublicationStatus.ACTIVE));
        assertThat(operationDefinition.getKind(), is(OperationKind.QUERY));
        assertThat(operationDefinition.getDescription(), is(NamedQueryPlainProvider.DESCRIPTION));
        assertThat(operationDefinition.getAffectsState(), is(false));
        assertThat("A system level search has no target resources", operationDefinition.getResource(), is(empty()));
        assertThat(operationDefinition.getSystem(), is(true));
        assertThat(operationDefinition.getType(), is(false));
        assertThat(operationDefinition.getInstance(), is(false));
        List<OperationDefinitionParameterComponent> parameters = operationDefinition.getParameter();
        assertThat(parameters.size(), is(1));
        OperationDefinitionParameterComponent param = parameters.get(0);
        assertThat(param.getName(), is(NamedQueryPlainProvider.SP_QUANTITY));
        assertThat(param.getType(), is("string"));
        assertThat(param.getSearchTypeElement().asStringValue(), is(RestSearchParameterTypeEnum.QUANTITY.getCode()));
        assertThat(param.getMin(), is(1));
        assertThat(param.getMax(), is("1"));
        assertThat(param.getUse(), is(OperationParameterUse.IN));
    }

    @Test
    public void testResourceLevelNamedQueryWithParameters() throws Exception {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new NamedQueryResourceProvider());
        rs.setServerAddressStrategy(new HardcodedServerAddressStrategy("http://localhost/baseR4"));

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        validate(conformance);

        CapabilityStatementRestComponent restComponent = conformance.getRest().get(0);
        CapabilityStatementRestResourceOperationComponent operationComponent = restComponent.getOperation().get(0);
        String operationReference = operationComponent.getDefinition();
        assertThat(operationReference, not(nullValue()));

        OperationDefinition operationDefinition = (OperationDefinition) sc.readOperationDefinition(new IdType(operationReference), createRequestDetails(rs));
        ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationDefinition));
        validate(operationDefinition);
        assertThat("The operation name should be the code if no description is set", operationDefinition.getName(), is("Search_" + NamedQueryResourceProvider.QUERY_NAME));
        String patientResourceName = "Patient";
        assertThat("A resource level search targets the resource of the provider it's defined in", operationDefinition.getResource().get(0).getValue(), is(patientResourceName));
        assertThat(operationDefinition.getSystem(), is(false));
        assertThat(operationDefinition.getType(), is(true));
        assertThat(operationDefinition.getInstance(), is(false));
        List<OperationDefinitionParameterComponent> parameters = operationDefinition.getParameter();
        assertThat(parameters.size(), is(1));
        OperationDefinitionParameterComponent param = parameters.get(0);
        assertThat(param.getName(), is(NamedQueryResourceProvider.SP_PARAM));
        assertThat(param.getType(), is("string"));
        assertThat(param.getSearchTypeElement().asStringValue(), is(RestSearchParameterTypeEnum.STRING.getCode()));
        assertThat(param.getMin(), is(0));
        assertThat(param.getMax(), is("1"));
        assertThat(param.getUse(), is(OperationParameterUse.IN));

        CapabilityStatementRestResourceComponent patientResource = restComponent.getResource().stream()
                .filter(r -> patientResourceName.equals(r.getType()))
                .findAny().get();
        assertThat("Named query parameters should not appear in the resource search params", patientResource.getSearchParam(), is(empty()));
    }

    @Test
    public void testExtendedOperationAtTypeLevel() throws Exception {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setProviders(new TypeLevelOperationProvider());
        rs.setServerAddressStrategy(new HardcodedServerAddressStrategy("http://localhost/baseR4"));

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

        validate(conformance);

        List<CapabilityStatementRestResourceOperationComponent> operations = conformance.getRest().get(0).getOperation();
        assertThat(operations.size(), is(1));
        assertThat(operations.get(0).getName(), is(TypeLevelOperationProvider.OPERATION_NAME));

        OperationDefinition opDef = (OperationDefinition) sc.readOperationDefinition(new IdType(operations.get(0).getDefinition()), createRequestDetails(rs));
        validate(opDef);
        assertEquals(TypeLevelOperationProvider.OPERATION_NAME, opDef.getCode());
        assertThat(opDef.getSystem(), is(false));
        assertThat(opDef.getType(), is(true));
        assertThat(opDef.getInstance(), is(false));
    }

    @Test
    public void testProfiledResourceStructureDefinitionLinks() throws Exception {
        RestfulServer rs = new RestfulServer(myCtx);
        rs.setResourceProviders(new ProfiledPatientProvider(), new MultipleProfilesPatientProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance));

        List<CapabilityStatementRestResourceComponent> resources = conformance.getRestFirstRep().getResource();
        CapabilityStatementRestResourceComponent patientResource = resources.stream()
                .filter(resource -> "Patient".equals(resource.getType()))
                .findFirst().get();
        assertThat(patientResource.getProfile(), containsString(PATIENT_SUB));
    }

    @Test
    public void testRevIncludes_Explicit() throws Exception {

        class PatientResourceProvider implements IResourceProvider {

            @Override
            public Class<Patient> getResourceType() {
                return Patient.class;
            }

            @Search
            public List<Patient> search(@IncludeParam(reverse = true, allow = {"Observation:foo", "Provenance:bar"}) Set<Include> theRevIncludes) {
                return Collections.emptyList();
            }

        }

        class ObservationResourceProvider implements IResourceProvider {

            @Override
            public Class<Observation> getResourceType() {
                return Observation.class;
            }

            @Search
            public List<Observation> search(@OptionalParam(name = "subject") ReferenceParam theSubject) {
                return Collections.emptyList();
            }

        }

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setResourceProviders(new PatientResourceProvider(), new ObservationResourceProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        sc.setRestResourceRevIncludesEnabled(true);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance));

        List<CapabilityStatementRestResourceComponent> resources = conformance.getRestFirstRep().getResource();
        CapabilityStatementRestResourceComponent patientResource = resources.stream()
                .filter(resource -> "Patient".equals(resource.getType()))
                .findFirst().get();
        assertThat(toStrings(patientResource.getSearchRevInclude()), containsInAnyOrder("Observation:foo", "Provenance:bar"));
    }

    @Test
    public void testRevIncludes_Inferred() throws Exception {

        class PatientResourceProvider implements IResourceProvider {

            @Override
            public Class<Patient> getResourceType() {
                return Patient.class;
            }

            @Search
            public List<Patient> search(@IncludeParam(reverse = true) Set<Include> theRevIncludes) {
                return Collections.emptyList();
            }

        }

        class ObservationResourceProvider implements IResourceProvider {

            @Override
            public Class<Observation> getResourceType() {
                return Observation.class;
            }

            @Search
            public List<Observation> search(@OptionalParam(name = "subject") ReferenceParam theSubject) {
                return Collections.emptyList();
            }

        }

        RestfulServer rs = new RestfulServer(myCtx);
        rs.setResourceProviders(new PatientResourceProvider(), new ObservationResourceProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider(rs);
        sc.setRestResourceRevIncludesEnabled(true);
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = (CapabilityStatement) sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        ourLog.info(myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance));

        List<CapabilityStatementRestResourceComponent> resources = conformance.getRestFirstRep().getResource();
        CapabilityStatementRestResourceComponent patientResource = resources.stream()
                .filter(resource -> "Patient".equals(resource.getType()))
                .findFirst().get();
        assertThat(toStrings(patientResource.getSearchRevInclude()), containsInAnyOrder("Observation:subject"));
    }

    private List<String> toOperationIdParts(List<CapabilityStatementRestResourceOperationComponent> theOperation) {
        ArrayList<String> retVal = Lists.newArrayList();
        for (CapabilityStatementRestResourceOperationComponent next : theOperation) {
            retVal.add(new IdType(next.getDefinition()).getIdPart());
        }
        return retVal;
    }

    private List<String> toOperationNames(List<CapabilityStatementRestResourceOperationComponent> theOperation) {
        ArrayList<String> retVal = Lists.newArrayList();
        for (CapabilityStatementRestResourceOperationComponent next : theOperation) {
            retVal.add(next.getName());
        }
        return retVal;
    }

    private String validate(IBaseResource theResource) {
        String conf = myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(theResource);
        ourLog.info("Def:\n{}", conf);

        ValidationResult result = myValidator.validateWithResult(conf);
        OperationOutcome operationOutcome = (OperationOutcome) result.toOperationOutcome();
        String outcome = myCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
        ourLog.info("Outcome: {}", outcome);

        assertTrue(result.isSuccessful(), outcome);
        List<OperationOutcome.OperationOutcomeIssueComponent> warningsAndErrors = operationOutcome
                .getIssue()
                .stream()
                .filter(t -> t.getSeverity().ordinal() <= OperationOutcome.IssueSeverity.WARNING.ordinal()) // <= because this enum has a strange order
                .collect(Collectors.toList());
        assertThat(outcome, warningsAndErrors, is(empty()));

        return myCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(theResource);
    }

    @SuppressWarnings("unused")
    public static class ConditionalProvider implements IResourceProvider {

        @Create
        public MethodOutcome create(@ResourceParam Patient thePatient, @ConditionalUrlParam String theConditionalUrl) {
            return null;
        }

        @Delete
        public MethodOutcome delete(@IdParam IdType theId, @ConditionalUrlParam(supportsMultiple = true) String theConditionalUrl) {
            return null;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @Update
        public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient, @ConditionalUrlParam String theConditionalUrl) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class InstanceHistoryProvider implements IResourceProvider {
        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @History
        public List<IBaseResource> history(@IdParam IdType theId) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class MultiOptionalProvider {

        @Search(type = Patient.class)
        public Patient findPatient(@Description(shortDefinition = "The patient's identifier") @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
                                   @Description(shortDefinition = "The patient's name") @OptionalParam(name = Patient.SP_NAME) StringParam theName) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class MultiTypeEncounterProvider implements IResourceProvider {

        @Operation(name = "someOp")
        public IBundleProvider everything(HttpServletRequest theServletRequest, @IdParam IdType theId,
                                          @OperationParam(name = "someOpParam1") DateType theStart, @OperationParam(name = "someOpParam2") Encounter theEnd) {
            return null;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Encounter.class;
        }

        @Validate
        public IBundleProvider validate(HttpServletRequest theServletRequest, @IdParam IdType theId, @ResourceParam Encounter thePatient) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class MultiTypePatientProvider implements IResourceProvider {

        @Operation(name = "someOp")
        public IBundleProvider everything(HttpServletRequest theServletRequest, @IdParam IdType theId,
                                          @OperationParam(name = "someOpParam1") DateType theStart, @OperationParam(name = "someOpParam2") Patient theEnd) {
            return null;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @Validate
        public IBundleProvider validate(HttpServletRequest theServletRequest, @IdParam IdType theId, @ResourceParam Patient thePatient) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class NonConditionalProvider implements IResourceProvider {

        @Create
        public MethodOutcome create(@ResourceParam Patient thePatient) {
            return null;
        }

        @Delete
        public MethodOutcome delete(@IdParam IdType theId) {
            return null;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @Update
        public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class PlainProviderWithExtendedOperationOnNoType {

        @Operation(name = "plain", idempotent = true, returnParameters = {@OperationParam(min = 1, max = 2, name = "out1", type = StringType.class)})
        public IBundleProvider everything(HttpServletRequest theServletRequest, @IdParam IdType theId, @OperationParam(name = "start") DateType theStart,
                                          @OperationParam(name = "end") DateType theEnd) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class ProviderWithExtendedOperationReturningBundle implements IResourceProvider {

        @Operation(name = "everything", idempotent = true)
        public IBundleProvider everything(HttpServletRequest theServletRequest, @IdParam IdType theId, @OperationParam(name = "start") DateType theStart,
                                          @OperationParam(name = "end") DateType theEnd) {
            return null;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

    }

    @SuppressWarnings("unused")
    public static class ProviderWithRequiredAndOptional {

        @Description(shortDefinition = "This is a search for stuff!")
        @Search
        public List<DiagnosticReport> findDiagnosticReportsByPatient(@RequiredParam(name = DiagnosticReport.SP_SUBJECT + '.' + Patient.SP_IDENTIFIER) TokenParam thePatientId,
                                                                     @OptionalParam(name = DiagnosticReport.SP_CODE) TokenOrListParam theNames, @OptionalParam(name = DiagnosticReport.SP_DATE) DateRangeParam theDateRange,
                                                                     @IncludeParam(allow = {"DiagnosticReport.result"}) Set<Include> theIncludes) throws Exception {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class ReadProvider {

        @Search(type = Patient.class)
        public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
            return null;
        }

        @Read(version = false)
        public Patient readPatient(@IdParam IdType theId) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class SearchProvider {

        @Search(type = Patient.class)
        public Patient findPatient1(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
            return null;
        }

        @Search(type = Patient.class)
        public Patient findPatient2(
                @Description(shortDefinition = "All patients linked to the given patient") @OptionalParam(name = "link", targetTypes = {Patient.class}) ReferenceAndListParam theLink) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class SearchProviderWithWhitelist {

        @Search(type = Patient.class)
        public Patient findPatient1(@Description(shortDefinition = "The organization at which this person is a patient") @RequiredParam(name = Patient.SP_ORGANIZATION, chainWhitelist = {"foo",
                "bar"}) ReferenceAndListParam theIdentifier) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class SearchProviderWithListNoType implements IResourceProvider {

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }


        @Search()
        public List<Patient> findPatient1(@Description(shortDefinition = "The organization at which this person is a patient") @RequiredParam(name = Patient.SP_ORGANIZATION) ReferenceAndListParam theIdentifier) {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class SearchProviderWithListWithType implements IResourceProvider {

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }


        @Search(type = Patient.class)
        public List<Patient> findPatient1(@Description(shortDefinition = "The organization at which this person is a patient") @RequiredParam(name = Patient.SP_ORGANIZATION) ReferenceAndListParam theIdentifier) {
            return null;
        }

    }

    public static class SystemHistoryProvider {

        @History
        public List<IBaseResource> history() {
            return null;
        }

    }

    public static class TypeHistoryProvider implements IResourceProvider {

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @History
        public List<IBaseResource> history() {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public static class VreadProvider {

        @Search(type = Patient.class)
        public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
            return null;
        }

        @Read(version = true)
        public Patient readPatient(@IdParam IdType theId) {
            return null;
        }

    }

    public static class TypeLevelOperationProvider implements IResourceProvider {

        public static final String OPERATION_NAME = "op";

        @Operation(name = OPERATION_NAME, idempotent = true)
        public IBundleProvider op() {
            return null;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

    }

    public static class NamedQueryPlainProvider {

        public static final String QUERY_NAME = "testQuery";
        public static final String DESCRIPTION = "A query description";
        public static final String SP_QUANTITY = "quantity";

        @Search(queryName = QUERY_NAME)
        @Description(formalDefinition = DESCRIPTION)
        public Bundle findAllGivenParameter(@RequiredParam(name = SP_QUANTITY) QuantityParam quantity) {
            return null;
        }
    }

    public static class NamedQueryResourceProvider implements IResourceProvider {

        public static final String QUERY_NAME = "testQuery";
        public static final String SP_PARAM = "param";

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @Search(queryName = QUERY_NAME)
        public Bundle findAllGivenParameter(@OptionalParam(name = SP_PARAM) StringParam param) {
            return null;
        }

    }

    public static class ProfiledPatientProvider implements IResourceProvider {

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return PatientSubSub2.class;
        }

        @Search
        public List<PatientSubSub2> find() {
            return null;
        }
    }

    public static class MultipleProfilesPatientProvider implements IResourceProvider {

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return PatientSubSub.class;
        }

        @Read(type = PatientTripleSub.class)
        public PatientTripleSub read(@IdParam IdType theId) {
            return null;
        }

    }

    @ResourceDef(id = PATIENT_SUB)
    public static class PatientSub extends Patient {
    }

    @ResourceDef(id = PATIENT_SUB_SUB)
    public static class PatientSubSub extends PatientSub {
    }

    @ResourceDef(id = PATIENT_SUB_SUB_2)
    public static class PatientSubSub2 extends PatientSub {
    }

    @ResourceDef(id = PATIENT_TRIPLE_SUB)
    public static class PatientTripleSub extends PatientSubSub {
    }

    private static Set<String> toStrings(Collection<? extends IPrimitiveType> theType) {
        HashSet<String> retVal = new HashSet<String>();
        for (IPrimitiveType next : theType) {
            retVal.add(next.getValueAsString());
        }
        return retVal;
    }

    @AfterAll
    public static void afterClassClearContext() {
        TestUtil.clearAllStaticFieldsForUnitTest();
    }

}
