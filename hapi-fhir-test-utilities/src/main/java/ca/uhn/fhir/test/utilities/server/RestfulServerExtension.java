package ca.uhn.fhir.test.utilities.server;

/*-
 * #%L
 * HAPI FHIR Test Utilities
 * %%
 * Copyright (C) 2014 - 2021 Smile CDR, Inc.
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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.test.utilities.JettyUtil;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RestfulServerExtension implements BeforeEachCallback, AfterEachCallback {
	private static final Logger ourLog = LoggerFactory.getLogger(RestfulServerExtension.class);

	private FhirContext myFhirContext;
	private List<Object> myProviders = new ArrayList<>();
	private FhirVersionEnum myFhirVersion;
	private Server myServer;
	private RestfulServer myServlet;
	private int myPort;
	private CloseableHttpClient myHttpClient;
	private IGenericClient myFhirClient;
	private List<Consumer<RestfulServer>> myConsumers = new ArrayList<>();

	/**
	 * Constructor
	 */
	public RestfulServerExtension(FhirContext theFhirContext, Object... theProviders) {
		Validate.notNull(theFhirContext);
		myFhirContext = theFhirContext;
		if (theProviders != null) {
			myProviders = new ArrayList<>(Arrays.asList(theProviders));
		}
	}

	/**
	 * Constructor: If this is used, it will create and tear down a FhirContext which is good for memory
	 */
	public RestfulServerExtension(FhirVersionEnum theFhirVersionEnum) {
		Validate.notNull(theFhirVersionEnum);
		myFhirVersion = theFhirVersionEnum;
	}

	private void createContextIfNeeded() {
		if (myFhirVersion != null) {
			myFhirContext = FhirContext.forCached(myFhirVersion);
		}
	}

	private void stopServer() throws Exception {
		JettyUtil.closeServer(myServer);
		myServer = null;
		myFhirClient = null;

		myHttpClient.close();
		myHttpClient = null;
	}

	private void startServer() throws Exception {
		myServer = new Server(0);

		ServletHandler servletHandler = new ServletHandler();
		myServlet = new RestfulServer(myFhirContext);
		myServlet.setDefaultPrettyPrint(true);
		if (myProviders != null) {
			myServlet.registerProviders(myProviders);
		}
		ServletHolder servletHolder = new ServletHolder(myServlet);
		servletHandler.addServletWithMapping(servletHolder, "/*");

		myConsumers.forEach(t -> t.accept(myServlet));

		myServer.setHandler(servletHandler);
		myServer.start();
		myPort = JettyUtil.getPortForStartedServer(myServer);
		ourLog.info("Server has started on port {}", myPort);
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		myHttpClient = builder.build();

		myFhirContext.getRestfulClientFactory().setSocketTimeout((int) (500 * DateUtils.MILLIS_PER_SECOND));
		myFhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		myFhirClient = myFhirContext.newRestfulGenericClient("http://localhost:" + myPort);
	}


	public IGenericClient getFhirClient() {
		return myFhirClient;
	}

	public FhirContext getFhirContext() {
		createContextIfNeeded();
		return myFhirContext;
	}

	public RestfulServer getRestfulServer() {
		return myServlet;
	}

	public int getPort() {
		return myPort;
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		stopServer();
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		createContextIfNeeded();
		startServer();
	}

	public RestfulServerExtension registerProvider(Object theProvider) {
		if (myServlet != null) {
			myServlet.registerProvider(theProvider);
		} else {
			myProviders.add(theProvider);
		}
		return this;
	}

	public RestfulServerExtension withServer(Consumer<RestfulServer> theConsumer) {
		if (myServlet != null) {
			theConsumer.accept(myServlet);
		} else {
			myConsumers.add(theConsumer);
		}
		return this;
	}

	public RestfulServerExtension registerInterceptor(Object theInterceptor) {
		return withServer(t -> t.getInterceptorService().registerInterceptor(theInterceptor));
	}

	public void shutDownServer() throws Exception {
		JettyUtil.closeServer(myServer);
	}
}
