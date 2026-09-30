package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.internal.ModifierGroupRepository;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

// The group is deleted between the existence check and the insert; the spy stands in for that lost race.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductReferenceRaceIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoSpyBean
	ModifierGroupRepository groups;

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void aGroupThatVanishedMidwayIsReportedAsUnknownNotAsADuplicateName() {
		var category = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		var categoryId = assertThat(category).bodyJson().extractingPath("$.id").actual().toString();
		doReturn(1L).when(groups).countByIdIn(any());

		var result = mvc.post()
			.uri("/api/v1/admin/products")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "Mango-%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
					  "displayOrder": 0, "quickSalePinned": false, "modifierGroupIds": ["%s"] }
					""".formatted(UUID.randomUUID(), categoryId, UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.unknown-modifier-group");
	}

}
