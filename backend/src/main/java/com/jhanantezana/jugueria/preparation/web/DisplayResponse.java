package com.jhanantezana.jugueria.preparation.web;

import java.util.List;

import com.jhanantezana.jugueria.preparation.internal.DisplayView;

record DisplayResponse(List<DisplayEntryResponse> preparing, List<DisplayEntryResponse> ready) {

	static DisplayResponse from(DisplayView view) {
		return new DisplayResponse(view.preparing().stream().map(DisplayEntryResponse::from).toList(),
				view.ready().stream().map(DisplayEntryResponse::from).toList());
	}

}
