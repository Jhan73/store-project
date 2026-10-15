package com.jhanantezana.jugueria.store.web;

import com.jhanantezana.jugueria.store.internal.StoreSettings;

record BoardThresholdsResponse(int boardWarningMinutes, int boardLateMinutes, String timeZone) {

	static BoardThresholdsResponse from(StoreSettings settings) {
		return new BoardThresholdsResponse(settings.getBoardWarningMinutes(), settings.getBoardLateMinutes(),
				settings.getTimeZone());
	}

}
