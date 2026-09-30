package com.jhanantezana.jugueria.catalog.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.shared.Money;

// Explicit SQL into records: the menu is a read model, so it skips the entities and their lazy collections.
@Component
class MenuQueries {

	private static final String VISIBLE_PRODUCTS = """
			FROM catalog.product p JOIN catalog.category c ON c.id = p.category_id
			WHERE p.active AND c.active
			""";

	private final JdbcClient jdbc;

	MenuQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// One snapshot for all the queries, so a change committed midway cannot leave the menu half old, half new.
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	MenuView load() {
		var categories = jdbc.sql("""
				SELECT id, name FROM catalog.category WHERE active ORDER BY display_order, name, id
				""").query((rs, row) -> new CategoryRow(rs.getObject("id", UUID.class), rs.getString("name"))).list();

		var productAllergens = allergens("""
				SELECT pa.product_id AS owner_id, pa.allergen_code
				FROM catalog.product_allergen pa JOIN catalog.product p ON p.id = pa.product_id
				JOIN catalog.category c ON c.id = p.category_id WHERE p.active AND c.active
				""");
		var optionAllergens = allergens("SELECT option_id AS owner_id, allergen_code FROM catalog.option_allergen");

		var options = jdbc.sql("""
				SELECT id, group_id, name, price_delta_amount, price_delta_currency, available
				FROM catalog.modifier_option ORDER BY display_order, id
				""")
			.query((rs, row) -> new OptionRow(rs.getObject("group_id", UUID.class),
					new MenuView.Option(rs.getObject("id", UUID.class), rs.getString("name"),
							money(rs, "price_delta"), rs.getBoolean("available"),
							optionAllergens.getOrDefault(rs.getObject("id", UUID.class), List.of()))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(OptionRow::groupId, LinkedHashMap::new,
					Collectors.mapping(OptionRow::option, Collectors.toList())));

		var groupsByProduct = jdbc.sql("""
				SELECT pmg.product_id, g.id, g.name, g.required, g.min_choices, g.max_choices
				FROM catalog.product_modifier_group pmg JOIN catalog.modifier_group g ON g.id = pmg.group_id
				JOIN catalog.product p ON p.id = pmg.product_id JOIN catalog.category c ON c.id = p.category_id
				WHERE p.active AND c.active ORDER BY pmg.product_id, pmg.display_order
				""")
			.query((rs, row) -> new GroupRow(rs.getObject("product_id", UUID.class),
					new MenuView.Group(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("required"),
							rs.getInt("min_choices"), rs.getInt("max_choices"),
							options.getOrDefault(rs.getObject("id", UUID.class), List.of()))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(GroupRow::productId, Collectors.mapping(GroupRow::group, Collectors.toList())));

		var productsByCategory = jdbc.sql("""
				SELECT p.id, p.category_id, p.name, p.description, p.price_amount, p.price_currency, p.available,
				       p.image_key
				""" + VISIBLE_PRODUCTS + " ORDER BY p.display_order, p.name, p.id")
			.query((rs, row) -> new ProductRow(rs.getObject("category_id", UUID.class),
					new MenuView.Product(rs.getObject("id", UUID.class), rs.getString("name"),
							rs.getString("description"), money(rs, "price"), rs.getString("image_key"),
							rs.getBoolean("available"),
							productAllergens.getOrDefault(rs.getObject("id", UUID.class), List.of()),
							groupsByProduct.getOrDefault(rs.getObject("id", UUID.class), List.of()))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(ProductRow::categoryId, Collectors.mapping(ProductRow::product, Collectors.toList())));

		return new MenuView(categories.stream()
			.map(category -> new MenuView.Category(category.id(), category.name(),
					productsByCategory.getOrDefault(category.id(), List.of())))
			.toList());
	}

	private Map<UUID, List<Allergen>> allergens(String sql) {
		var rows = jdbc.sql(sql)
			.query((rs, row) -> Map.entry(rs.getObject("owner_id", UUID.class),
					Allergen.valueOf(rs.getString("allergen_code"))))
			.list();
		var byOwner = new HashMap<UUID, List<Allergen>>();
		rows.forEach(row -> byOwner.computeIfAbsent(row.getKey(), key -> new ArrayList<>()).add(row.getValue()));
		byOwner.values().forEach(list -> list.sort(Comparator.naturalOrder()));
		return byOwner;
	}

	private static Money money(ResultSet rs, String column) throws SQLException {
		return new Money(rs.getBigDecimal(column + "_amount"), Currency.getInstance(rs.getString(column + "_currency").strip()));
	}

	private record CategoryRow(UUID id, String name) {
	}

	private record OptionRow(UUID groupId, MenuView.Option option) {
	}

	private record GroupRow(UUID productId, MenuView.Group group) {
	}

	private record ProductRow(UUID categoryId, MenuView.Product product) {
	}

}
