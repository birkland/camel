package org.apache.camel.component.context;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Verifies that endpoint names within different blackbox camel contexts do not
 * interfere with one another.
 * <p>
 * Given two "black box" camel contexts <code>blackBox1</code> and
 * <code>blackBox2</code>, a third "enclosing" camel context may refer to
 * endpoints within them via logical URIs such as <code>blackBox1:in</code> or
 * <code>blackBox2:in</code>
 * </p>
 * <p>
 * This test verifies that while two different blackbox contexts can have the
 * same local name for endpoints within them, their logical URIs are distinct
 * and may both by referenced by a third context. Furthermore, this third,
 * enclosing context may itself have similarly named local endpoints (e.g.
 * direct:in) that shouldn't conflict with endpoints in the blackbox contexts
 * (their own direct:in endpojnts).
 * </p>
 *
 */
public class BlackBoxEndpointIsolationTest extends CamelTestSupport {
	@Produce
	protected ProducerTemplate template;

	@EndpointInject(uri = "mock:out")
	private MockEndpoint mock_out;

	private JndiRegistry registry;

	private CamelContext enclosingContext;

	@Test
	public void testBlackBoxRoutes() throws Exception {

		/* CamelContext names for the black box contexts */
		final String BLACK_BOX_1 = "blackBox1";
		final String BLACK_BOX_2 = "blackBox2";

		/*
		 * These are the context component URIs (e.g. blackBox1:in,
		 * blackBox2:out, etc)
		 */
		final String BLACK_BOX_1_IN = BLACK_BOX_1 + ":in";
		final String BLACK_BOX_2_IN = BLACK_BOX_2 + ":in";
		final String BLACK_BOX_1_OUT = BLACK_BOX_1 + ":out";
		final String BLACK_BOX_2_OUT = BLACK_BOX_2 + ":out";

		/* Create first black box context */
		newBlackBoxContext(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("direct:in").setHeader(BLACK_BOX_1, constant(BLACK_BOX_1))
						.to("direct:out");

			}
		}, BLACK_BOX_1);

		/* Create second black box context */
		newBlackBoxContext(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("direct:in").setHeader(BLACK_BOX_2, constant(BLACK_BOX_2))
						.to("direct:out");

			}
		}, BLACK_BOX_2);

		/* Now wire the two black boxes together within our enclosing context */
		enclosingContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:in").id("BEGIN").to(BLACK_BOX_1_IN);
				from(BLACK_BOX_1_OUT).id("MIDDLE").to(BLACK_BOX_2_IN);
				from(BLACK_BOX_2_OUT).id("END").to("direct:out");
			}
		});

		/* Verify that one message passes through the black boxes */
		mock_out.expectedMessageCount(1);

		/*
		 * Verify that the resulting message truly did pass through the black
		 * boxes. Each black box sets a header with key and value set to the
		 * black box's ID.
		 */
		mock_out.expectedHeaderReceived(BLACK_BOX_1, BLACK_BOX_1);
		mock_out.expectedHeaderReceived(BLACK_BOX_2, BLACK_BOX_2);

		/*
		 * Finally, send a single message to 'direct:in" in the enclosing camel
		 * context
		 */
		template.sendBody("direct:in", "testing");

		mock_out.assertIsSatisfied();

	}

	/* Create a new camel context with the given routes and name */
	private CamelContext newBlackBoxContext(RoutesBuilder routes, String id)
			throws Exception {
		CamelContext cxt = new DefaultCamelContext(registry);
		cxt.setNameStrategy(new ExplicitCamelContextNameStrategy(id));
		cxt.addRoutes(routes);
		cxt.start();

		registry.bind(id, cxt);
		return cxt;

	}

	protected JndiRegistry createRegistry() throws Exception {

		return registry = super.createRegistry();
	}

	@Override
	protected CamelContext createCamelContext() throws Exception {

		return enclosingContext = super.createCamelContext();
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {

		return new RouteBuilder() {

			public void configure() {

				from("direct:out").to("mock:out");
			}
		};
	}
}
