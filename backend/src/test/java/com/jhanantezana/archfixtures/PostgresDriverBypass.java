package com.jhanantezana.archfixtures;

import org.postgresql.PGConnection;

public class PostgresDriverBypass {

	public Class<?> pgConnectionType() {
		return PGConnection.class;
	}

}
