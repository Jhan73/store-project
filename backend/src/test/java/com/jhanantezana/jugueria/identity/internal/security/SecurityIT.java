package com.jhanantezana.jugueria.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.probe.SecuredProbeController;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SecuredProbeController.class })
class SecurityIT {

	static final String PROTECTED = "/api/v1/probe/cashier";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	AccessTokenIssuer issuer;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JwtDecoder decoder;

	@Autowired
	IdentityProperties properties;

	@Test
	void rejectsARequestWithoutATokenAsProblemDetails() {
		var result = mvc.get().uri(PROTECTED).exchange();

		assertProblem(result, HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.WWW_AUTHENTICATE,
				values -> assertThat(values).singleElement().asString().startsWith("Bearer"));
	}

	@Test
	void rejectsARoleThatIsNotAllowed() {
		assertProblem(call(issuer.issue(UUID.randomUUID(), Role.SERVER)), HttpStatus.FORBIDDEN, "auth.forbidden");
	}

	@Test
	void letsAnAllowedRoleThroughAsTheCurrentActor() {
		var id = UUID.randomUUID();

		var result = call(issuer.issue(id, Role.CASHIER));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(id.toString());
		assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("CASHIER");
	}

	@Test
	void issuesShortLivedTokensWithTheAgreedClaims() {
		var id = UUID.randomUUID();

		var jwt = decoder.decode(issuer.issue(id, Role.ADMIN));

		assertThat(jwt.getSubject()).isEqualTo(id.toString());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(jwt.getAudience()).containsExactly(properties.jwt().audience());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(properties.jwt().issuer());
		assertThat(jwt.getId()).isNotBlank();
		assertThat(jwt.getHeaders()).containsEntry("kid", properties.jwt().keyId());
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void rejectsAnExpiredToken() {
		var issuedAt = Instant.now().minus(Duration.ofHours(1));

		var token = encode(encoder, claims(properties.jwt().issuer(), properties.jwt().audience())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(Duration.ofMinutes(15))));

		assertProblem(call(token), HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
	}

	@Test
	void rejectsATokenForAnotherIssuerOrAudience() {
		var foreignIssuer = encode(encoder, claims("someone-else", properties.jwt().audience()));
		var foreignAudience = encode(encoder, claims(properties.jwt().issuer(), "another-api"));

		assertProblem(call(foreignIssuer), HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
		assertProblem(call(foreignAudience), HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() {
		var otherKey = SigningKeys.rsaKey(properties.jwt().keyId(), null, true);
		var forger = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));

		var token = encode(forger, claims(properties.jwt().issuer(), properties.jwt().audience()));

		assertProblem(call(token), HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
	}

	@Test
	void letsAnAllowedRoleThroughUsingTheReusableAuthTestHelper() {
		var id = UUID.randomUUID();

		var result = mvc.get().uri(PROTECTED).with(AuthenticatedAs.user(id, Role.CASHIER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(id.toString());
	}

	@Test
	void answersTheProtectedResourceMetadataEndpointTruthfully() {
		var result = mvc.get().uri("/.well-known/oauth-protected-resource").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$.tls_client_certificate_bound_access_tokens")
			.isEqualTo(false);
	}

	@Test
	void answersCorsPreflightsOnlyForTheFrontendOrigin() {
		var allowed = preflight(properties.allowedOrigins().getFirst());
		var foreign = preflight("https://evil.example");

		assertThat(allowed).hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, properties.allowedOrigins().getFirst())
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
		assertThat(foreign).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult call(String token) {
		return mvc.get().uri(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private MvcTestResult preflight(String origin) {
		return mvc.options()
			.uri(PROTECTED)
			.header(HttpHeaders.ORIGIN, origin)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
			.exchange();
	}

	private JwtClaimsSet.Builder claims(String issuer, String audience) {
		var now = Instant.now();
		return JwtClaimsSet.builder()
			.issuer(issuer)
			.audience(List.of(audience))
			.subject(UUID.randomUUID().toString())
			.claim("roles", List.of("CASHIER"))
			.issuedAt(now)
			.expiresAt(now.plus(Duration.ofMinutes(15)));
	}

	private String encode(JwtEncoder jwtEncoder, JwtClaimsSet.Builder claims) {
		var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.jwt().keyId()).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
		assertThat(result).bodyJson().extractingPath("$.correlationId").isNotNull();
	}

}
