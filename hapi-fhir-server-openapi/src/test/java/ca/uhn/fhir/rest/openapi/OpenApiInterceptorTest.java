package ca.uhn.fhir.rest.openapi;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Patch;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import ca.uhn.fhir.rest.server.provider.BaseLastNProvider;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.test.utilities.server.HashMapResourceProviderExtension;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OpenApiInterceptorTest {

	private static final Logger ourLog = LoggerFactory.getLogger(OpenApiInterceptorTest.class);
	private FhirContext myFhirContext = FhirContext.forCached(FhirVersionEnum.R4);
	@RegisterExtension
	@Order(0)
	protected RestfulServerExtension myServer = new RestfulServerExtension(myFhirContext)
		.withPort(8000) // FIXME: remove
		.withServletPath("/fhir/*")
		.withServer(t -> t.registerProvider(new MyLastNProvider()))
		.withServer(t -> t.registerInterceptor(new ResponseHighlighterInterceptor()));
	@RegisterExtension
	@Order(1)
	protected HashMapResourceProviderExtension<Patient> myPatientProvider = new HashMapResourceProviderExtension<>(myServer, Patient.class);
	@RegisterExtension
	@Order(2)
	protected HashMapResourceProviderExtension<Observation> myObservationProvider = new HashMapResourceProviderExtension<>(myServer, Observation.class);
	private CloseableHttpClient myClient;

	@BeforeEach
	public void before() {
		myClient = HttpClientBuilder.create().build();
	}

	@AfterEach
	public void after() throws IOException {
		myClient.close();
		myServer.getRestfulServer().getInterceptorService().unregisterAllInterceptors();
	}

	@Test
	public void testFetchSwagger() throws IOException {
		myServer.getRestfulServer().registerInterceptor(new OpenApiInterceptor());

		String resp;
		HttpGet get = new HttpGet("http://localhost:" + myServer.getPort() + "/fhir/metadata?_pretty=true");
		try (CloseableHttpResponse response = myClient.execute(get)) {
			resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info("CapabilityStatement: {}", resp);
		}

		get = new HttpGet("http://localhost:" + myServer.getPort() + "/fhir/api-docs");
		try (CloseableHttpResponse response = myClient.execute(get)) {
			resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info("Response: {}", response.getStatusLine());
			ourLog.info("Response: {}", resp);
		}

		OpenAPI parsed = Yaml.mapper().readValue(resp, OpenAPI.class);

		PathItem fooOpPath = parsed.getPaths().get("/$foo-op");
		assertNull(fooOpPath.getGet());
		assertNotNull(fooOpPath.getPost());
		assertEquals("Foo Op Description", fooOpPath.getPost().getDescription());
		assertEquals("Foo Op Short", fooOpPath.getPost().getSummary());

		PathItem lastNPath = parsed.getPaths().get("/Observation/$lastn");
		assertNull(lastNPath.getPost());
		assertNotNull(lastNPath.getGet());
		assertEquals("LastN Description", lastNPath.getGet().getDescription());
		assertEquals("LastN Short", lastNPath.getGet().getSummary());
		assertEquals(4, lastNPath.getGet().getParameters().size());
		assertEquals("Subject description", lastNPath.getGet().getParameters().get(0).getDescription());
	}

	public static class MyLastNProvider {


		@Description(value = "LastN Description", shortDefinition = "LastN Short")
		@Operation(name = Constants.OPERATION_LASTN, typeName = "Observation", idempotent = true)
		public IBaseBundle lastN(
			@Description(value = "Subject description", shortDefinition = "Subject short")
			@OperationParam(name = "subject", typeName = "reference", min = 0, max = 1) IBaseReference theSubject,
			@OperationParam(name = "category", typeName = "coding", min = 0, max = OperationParam.MAX_UNLIMITED) List<IBaseCoding> theCategories,
			@OperationParam(name = "code", typeName = "coding", min = 0, max = OperationParam.MAX_UNLIMITED) List<IBaseCoding> theCodes,
			@OperationParam(name = "max", typeName = "integer", min = 0, max = 1) IPrimitiveType<Integer> theMax
		) {
			throw new IllegalStateException();
		}

		@Description(value = "Foo Op Description", shortDefinition = "Foo Op Short")
		@Operation(name = "foo-op", idempotent = false)
		public IBaseBundle foo(
			ServletRequestDetails theRequestDetails,
			@Description(shortDefinition = "Reference description")
			@OperationParam(name = "subject", typeName = "reference", min = 0, max = 1) IBaseReference theSubject,
			@OperationParam(name = "category", typeName = "coding", min = 0, max = OperationParam.MAX_UNLIMITED) List<IBaseCoding> theCategories,
			@OperationParam(name = "code", typeName = "coding", min = 0, max = OperationParam.MAX_UNLIMITED) List<IBaseCoding> theCodes,
			@OperationParam(name = "max", typeName = "integer", min = 0, max = 1) IPrimitiveType<Integer> theMax
		) {
			throw new IllegalStateException();
		}

		@Patch(type = Patient.class)
		public MethodOutcome patch(HttpServletRequest theRequest, @IdParam IIdType theId, @ConditionalUrlParam String theConditionalUrl, RequestDetails theRequestDetails, @ResourceParam String theBody, PatchTypeEnum thePatchType, @ResourceParam IBaseParameters theRequestBody) {
			throw new IllegalStateException();
		}


	}
}
