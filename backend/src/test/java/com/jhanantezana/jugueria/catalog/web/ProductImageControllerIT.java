package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.catalog.internal.ProductImageStorage;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"jugueria.catalog.images.public-base-url=https://media.example.test/" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ProductImageControllerIT {

	static final String PRODUCTS = "/api/v1/admin/products";

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };

	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 9 };

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@MockitoBean
	ProductImageStorage storage;

	@BeforeEach
	void freshMock() {
		reset(storage);
	}

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void storesAnImageUnderAContentHashedKeyAndPointsTheProductAtIt() throws Exception {
		var product = createProduct();

		var result = upload(idOf(product), etagOf(product), "mango.png", "image/png", PNG);

		var key = "products/" + sha256(PNG) + ".png";
		verify(storage).put(eq(key), eq(PNG), eq("image/png"));
		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.imageUrl").isEqualTo("https://media.example.test/" + key);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		assertThat(events.stream(ProductChanged.class).toList().get(1)).satisfies(event -> {
			assertThat(event.before().imageKey()).isNull();
			assertThat(event.after().imageKey()).isEqualTo(key);
		});
	}

	@Test
	void trustsTheBytesNotTheClientsContentTypeOrFileName() throws Exception {
		var product = createProduct();

		var result = upload(idOf(product), etagOf(product), "photo.png", "image/png", JPEG);

		assertThat(result).hasStatusOk();
		verify(storage).put(eq("products/" + sha256(JPEG) + ".jpg"), eq(JPEG), eq("image/jpeg"));
	}

	@Test
	void theStorageCallRunsOutsideAnyDatabaseTransaction() {
		var product = createProduct();
		var transactional = new AtomicBoolean(true);
		doAnswer(invocation -> {
			transactional.set(TransactionSynchronizationManager.isActualTransactionActive());
			return null;
		}).when(storage).put(any(), any(), any());

		upload(idOf(product), etagOf(product), "mango.png", "image/png", PNG);

		assertThat(transactional).isFalse();
	}

	@Test
	void replacingTheImageUsesANewKeyAndKeepsTheOldObject() throws Exception {
		var product = createProduct();
		var first = upload(idOf(product), etagOf(product), "a.png", "image/png", PNG);

		var second = upload(idOf(product), etagOf(first), "b.jpg", "image/jpeg", JPEG);

		assertThat(second).hasStatusOk();
		assertThat(second).bodyJson().extractingPath("$.imageUrl")
			.isEqualTo("https://media.example.test/products/" + sha256(JPEG) + ".jpg");
		assertThat(second).bodyJson().extractingPath("$.etag").isEqualTo("\"2\"");
	}

	@Test
	void uploadingTheImageTheProductAlreadyHasChangesNothingAndCallsNoStorage() {
		var product = createProduct();
		var first = upload(idOf(product), etagOf(product), "a.png", "image/png", PNG);
		reset(storage);

		var again = upload(idOf(product), etagOf(first), "a.png", "image/png", PNG);

		assertThat(again).hasStatusOk();
		assertThat(again).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		verify(storage, never()).put(any(), any(), any());
		assertThat(events.stream(ProductChanged.class)).hasSize(2);
	}

	@Test
	void theMenuShowsTheNewImageRightAway() throws Exception {
		var product = createProduct();
		var before = mvc.get().uri("/api/v1/catalog/menu").exchange().getResponse().getHeader(HttpHeaders.ETAG);

		upload(idOf(product), etagOf(product), "mango.png", "image/png", PNG);

		var menu = mvc.get().uri("/api/v1/catalog/menu").exchange();
		assertThat(menu.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(before);
		assertThat(menu).bodyJson()
			.extractingPath("$.categories[*].products[?(@.id=='" + idOf(product) + "')].imageUrl")
			.asList()
			.containsExactly("https://media.example.test/products/" + sha256(PNG) + ".png");
	}

	@Test
	void rejectsContentThatIsNotAnImage() {
		var product = createProduct();

		var result = upload(idOf(product), etagOf(product), "evil.png", "image/png", "<script>".getBytes());

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-image");
		verify(storage, never()).put(any(), any(), any());
	}

	@Test
	void rejectsAnEmptyFile() {
		var product = createProduct();

		var result = upload(idOf(product), etagOf(product), "empty.png", "image/png", new byte[0]);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-image");
	}

	@Test
	void rejectsAFileOverTheSizeLimit() {
		var product = createProduct();
		var big = new byte[3 * 1024 * 1024];
		System.arraycopy(PNG, 0, big, 0, PNG.length);

		var result = upload(idOf(product), etagOf(product), "big.png", "image/png", big);

		assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.content-too-large");
		verify(storage, never()).put(any(), any(), any());
	}

	@Test
	void rejectsARequestWithoutTheFilePart() {
		var product = createProduct();

		var result = mvc.put()
			.multipart()
			.uri(PRODUCTS + "/" + idOf(product) + "/image")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(product))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsAnUploadWithoutIfMatchBeforeTouchingTheStorage() {
		var product = createProduct();

		var result = mvc.put()
			.multipart()
			.uri(PRODUCTS + "/" + idOf(product) + "/image")
			.with(admin())
			.file(new MockMultipartFile("file", "a.png", "image/png", PNG))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		verify(storage, never()).put(any(), any(), any());
	}

	@Test
	void aStaleUploadIsRejectedBeforeTouchingTheStorage() {
		var product = createProduct();

		var result = upload(idOf(product), "\"7\"", "a.png", "image/png", PNG);

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(result).bodyJson().extractingPath("$.currentETag").isEqualTo("\"0\"");
		verify(storage, never()).put(any(), any(), any());
	}

	@Test
	void reportsAnUnknownProduct() {
		var result = upload(UUID.randomUUID().toString(), "\"0\"", "a.png", "image/png", PNG);

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-not-found");
	}

	@Test
	void aStorageOutageLeavesTheProductUntouchedAndAnswers503() {
		var product = createProduct();
		doThrow(new BusinessException(CatalogError.PROVIDER_UNAVAILABLE, "Image storage is unavailable")).when(storage)
			.put(any(), any(), any());

		var result = upload(idOf(product), etagOf(product), "a.png", "image/png", PNG);

		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.provider-unavailable");
		var current = mvc.get().uri(PRODUCTS + "/" + idOf(product)).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$.imageUrl").isNull();
		assertThat(current).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
	}

	@Test
	void removesTheImage() {
		var product = createProduct();
		var uploaded = upload(idOf(product), etagOf(product), "a.png", "image/png", PNG);

		var result = mvc.delete()
			.uri(PRODUCTS + "/" + idOf(product) + "/image")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(uploaded))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.imageUrl").isNull();
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"2\"");
		verify(storage, times(1)).put(any(), any(), any());
	}

	@Test
	void removingAnAbsentImageChangesNothing() {
		var product = createProduct();

		var result = mvc.delete()
			.uri(PRODUCTS + "/" + idOf(product) + "/image")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(product))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
		assertThat(events.stream(ProductChanged.class)).hasSize(1);
	}

	@Test
	void rejectsARemovalWithoutIfMatch() {
		var product = createProduct();

		var result = mvc.delete().uri(PRODUCTS + "/" + idOf(product) + "/image").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void rejectsEveryEndpointWhenAnonymous() {
		var id = UUID.randomUUID();

		assertThat(mvc.put()
			.multipart()
			.uri(PRODUCTS + "/" + id + "/image")
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.file(new MockMultipartFile("file", "a.png", "image/png", PNG))
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri(PRODUCTS + "/" + id + "/image").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsEveryEndpointForNonAdminRoles() {
		var id = UUID.randomUUID();
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.put()
				.multipart()
				.uri(PRODUCTS + "/" + id + "/image")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.file(new MockMultipartFile("file", "a.png", "image/png", PNG))
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.delete()
				.uri(PRODUCTS + "/" + id + "/image")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	private MvcTestResult upload(String productId, String etag, String fileName, String contentType, byte[] content) {
		return mvc.put()
			.multipart()
			.uri(PRODUCTS + "/" + productId + "/image")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.file(new MockMultipartFile("file", fileName, contentType, content))
			.exchange();
	}

	private MvcTestResult createProduct() {
		var category = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		return mvc.post().uri(PRODUCTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Mango-%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
				  "displayOrder": 0, "quickSalePinned": false }
				""".formatted(UUID.randomUUID(), idOf(category))).exchange();
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String etagOf(MvcTestResult result) {
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
