package com.jhanantezana.jugueria.identity.internal.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Role;

@Component
public class AccessTokenIssuer {

	static final String ROLES_CLAIM = "roles";

	private final JwtEncoder encoder;

	private final IdentityProperties.Jwt jwt;

	private final Clock clock;

	AccessTokenIssuer(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
		this.encoder = encoder;
		this.jwt = properties.jwt();
		this.clock = clock;
	}

	public String issue(UUID userId, Role role) {
		var now = Instant.now(clock);
		var claims = JwtClaimsSet.builder()
			.issuer(jwt.issuer())
			.audience(List.of(jwt.audience()))
			.subject(userId.toString())
			.claim(ROLES_CLAIM, List.of(role.name()))
			.issuedAt(now)
			.expiresAt(now.plus(jwt.accessTokenTtl()))
			.id(Ids.newId().toString())
			.build();
		var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwt.keyId()).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
