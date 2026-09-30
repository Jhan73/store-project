package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;

import software.amazon.awssdk.services.s3.S3Client;

// Tests never reach real AWS: with no profile the safe adapter is the only one, and no S3 client exists.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class ProductImageStorageProfileIT {

	@Autowired
	ProductImageStorage storage;

	@Autowired
	ApplicationContext context;

	@Test
	void resolvesTheSafeAdapterWithNoProfileActive() {
		assertThat(storage).isInstanceOf(UnavailableProductImageStorage.class);
	}

	@Test
	void theSafeAdapterAnswersServiceUnavailable() {
		assertThatThrownBy(() -> storage.put("products/a.png", new byte[] { 1 }, "image/png"))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(CatalogError.PROVIDER_UNAVAILABLE));
	}

	@Test
	void neverCreatesAnS3ClientWithNoProfileActive() {
		assertThatThrownBy(() -> context.getBean(S3Client.class)).isInstanceOf(NoSuchBeanDefinitionException.class);
	}

}
