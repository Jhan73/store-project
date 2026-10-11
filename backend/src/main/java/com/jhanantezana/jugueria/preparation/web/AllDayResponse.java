package com.jhanantezana.jugueria.preparation.web;

import java.util.List;

import com.jhanantezana.jugueria.preparation.internal.AllDayView;

record AllDayResponse(List<AllDayGroupResponse> groups, List<AllDayLineResponse> individual) {

	static AllDayResponse from(AllDayView view) {
		return new AllDayResponse(view.groups().stream().map(AllDayGroupResponse::from).toList(),
				view.individual().stream().map(AllDayLineResponse::from).toList());
	}

}
