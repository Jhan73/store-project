package com.jhanantezana.jugueria.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Money(
		@JsonFormat(shape = JsonFormat.Shape.STRING) @Column(precision = 12, scale = 2) BigDecimal amount,
		@JdbcTypeCode(SqlTypes.CHAR) @Column(length = 3) Currency currency) implements Comparable<Money> {

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
		try {
			amount = amount.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException e) {
			throw new IllegalArgumentException("Money has at most two decimals: " + amount, e);
		}
	}

	public static Money of(String amount, Currency currency) {
		return new Money(new BigDecimal(amount), currency);
	}

	public static Money zero(Currency currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	public Money times(int quantity) {
		return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
	}

	@JsonIgnore
	public boolean isZero() {
		return amount.signum() == 0;
	}

	@JsonIgnore
	public boolean isNegative() {
		return amount.signum() < 0;
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount);
	}

	private void requireSameCurrency(Money other) {
		if (!currency.equals(other.currency)) {
			throw new IllegalArgumentException("Currency mismatch: " + currency + " and " + other.currency);
		}
	}

}
